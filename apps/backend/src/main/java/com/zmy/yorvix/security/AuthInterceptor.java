package com.zmy.yorvix.security;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.i18n.Messages;
import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.service.system.MenuService;
import com.zmy.yorvix.service.system.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 认证与鉴权拦截器。职责链：
 * 登录态校验 -> 用户状态校验 -> 令牌版本校验 -> 角色注入 -> 接口鉴权。
 * <p>Opaque Token 方案：token 有效性 = Redis 中是否存在对应 key（登出即删除，立即生效），
 * 且载荷中的 tokenVersion 需与 {@code sys_user.token_version} 一致
 * （改密 / 重置密码 / 禁用后递增，可一次性作废该用户全部令牌）。
 * <p>角色每次请求实时查库注入 AuthContext，角色变更即时生效。
 * <p><b>本类只负责写入 {@link AuthContext}，不负责清理</b>——清理由
 * {@link AuthContextFilter} 统一在 {@code finally} 中完成。原因是本类在鉴权失败时
 * 返回 false，Spring 不会再回调 {@code afterCompletion}（见 AuthContextFilter 的注释），
 * 把清理放在那里会在这条路径上静默失效。
 * <p>接口鉴权有两层，均返回真实 HTTP 403：
 * <ul>
 *   <li>{@link RequiresRoles} —— 按角色编码判定，用于平台治理能力（{@code /api/system/**}）；</li>
 *   <li>{@link RequiresPermissions} —— 按按钮权限码判定，用于业务能力。
 *       权限码**懒加载**：只有命中该注解的接口才查一次，未标注的接口零额外开销。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

  private final TokenProperties tokenProperties;
  private final TokenService tokenService;
  private final SysUserMapper userMapper;
  private final RoleService roleService;
  private final MenuService menuService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    // 免鉴权路径
    if (isWhiteList(request.getRequestURI())) {
      return true;
    }
    // 非控制器方法（如静态资源）直接放行
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    // 1. 取 token
    String token = resolveToken(request);
    if (!StringUtils.hasText(token)) {
      return writeUnauthorized(response, "error.token.missing");
    }

    // 2. Redis 校验（不存在即无效/已过期/已登出）
    TokenService.TokenPayload payload = tokenService.resolveAccess(token).orElse(null);
    if (payload == null) {
      return writeUnauthorized(response, "error.token.invalid");
    }

    // 3. 用户状态
    SysUser user = userMapper.selectById(payload.userId());
    if (user == null) {
      return writeUnauthorized(response, "error.user.not.found");
    }
    if (!user.isEnabled()) {
      return writeUnauthorized(response, "error.account.disabled");
    }
    // 令牌版本：改密 / 重置密码 / 禁用后版本递增，旧令牌立即失效（不必等 TTL 过期）。
    // 用户行本来就要查，所以这一步是零额外 I/O
    if (!user.matchesTokenVersion(payload.tokenVersion())) {
      return writeUnauthorized(response, "error.login.expired");
    }

    // 4. 角色注入（实时查库，变更即时生效）。
    //    传入上面已加载的 is_super，避免 getRoleCodes 内部再读一次同一行 sys_user
    List<String> roles = roleService.getRoleCodes(user.getId(), user.isSuperUser());
    AuthContext.set(new LoginUser(user.getId(), user.getUsername(), user.getNickname(), roles,
        user.isSuperUser()));

    // 5. @RequiresRoles 接口鉴权（需满足全部角色）
    RequiresRoles requiredRoles = findAnnotation(handlerMethod, RequiresRoles.class);
    if (requiredRoles != null && !hasAllRoles(roles, requiredRoles.value())) {
      return writeForbidden(response);
    }

    // 6. @RequiresPermissions 接口鉴权（业务域的细粒度边界）。
    //    懒加载：只有标注了该注解的接口才查权限码，绝大多数请求不会走到这里，
    //    因此这一步不会给全站请求增加查询开销
    RequiresPermissions requiredPermissions =
        findAnnotation(handlerMethod, RequiresPermissions.class);
    if (requiredPermissions != null) {
      Set<String> granted = menuService.authCodesOf(AuthContext.require());
      if (!granted.containsAll(Arrays.asList(requiredPermissions.value()))) {
        log.debug("权限码不足: userId={}, required={}, granted={}",
            user.getId(), Arrays.toString(requiredPermissions.value()), granted);
        return writeForbidden(response);
      }
    }
    return true;
  }

  /** 读取方法或类上的注解（方法优先，与 Spring 的合并语义一致） */
  private <A extends Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> type) {
    A annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), type);
    return annotation == null
        ? AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), type)
        : annotation;
  }

  /** 全部满足语义：用户角色需包含注解声明的所有角色 */
  private boolean hasAllRoles(List<String> userRoles, String[] required) {
    return userRoles != null && userRoles.containsAll(Arrays.asList(required));
  }

  /**
   * 免鉴权判定。匹配语义见 {@link WhiteList}（精确匹配，子树需显式写 {@code /**}）。
   * <p>白名单为空时全部走鉴权（fail-closed）；认证链路必需项是否齐备由
   * {@code StartupChecks} 在启动时校验，避免"漏配 = 谁也登不进来"却在运行期才暴露。
   */
  private boolean isWhiteList(String uri) {
    return WhiteList.anyCovers(tokenProperties.getSecurity().getWhiteList(), uri);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }

  /**
   * 返回真实 HTTP 401，vben 前端据此触发重新认证/刷新令牌。
   *
   * @param messageKey i18n key（见 resources/i18n/messages*.properties），
   *                   在这里按请求语言解析为面向用户的文案
   */
  private boolean writeUnauthorized(HttpServletResponse response, String messageKey)
      throws Exception {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return writeResult(response,
        Result.fail(ResultCode.UNAUTHORIZED.getCode(), Messages.get(messageKey)));
  }

  private boolean writeForbidden(HttpServletResponse response) throws Exception {
    // 返回真实 HTTP 403：已登录但角色不满足
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return writeResult(response, Result.fail(ResultCode.FORBIDDEN));
  }

  private boolean writeResult(HttpServletResponse response, Result<?> result) throws Exception {
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(result));
    return false;
  }
}
