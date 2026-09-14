package com.zmy.yorvix.security;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 认证与鉴权拦截器（职责：登录态校验 -> 用户状态校验 -> 权限校验）。
 * <p>Opaque Token 方案：token 有效性 = Redis 中是否存在对应 key（登出即删除，立即失效）。
 * <p>约定：失败时返回真实 HTTP 401，vben 前端据此触发重新认证/刷新令牌。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

  private final TokenProperties tokenProperties;
  private final TokenService tokenService;
  private final SysUserMapper userMapper;
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
    if (!(handler instanceof HandlerMethod)) {
      return true;
    }

    // 1. 取 token
    String token = resolveToken(request);
    if (!StringUtils.hasText(token)) {
      return writeUnauthorized(response, "缺少访问令牌");
    }

    // 2. Redis 校验（不存在即无效/已过期/已登出）
    TokenService.TokenPayload payload = tokenService.resolveAccess(token).orElse(null);
    if (payload == null) {
      return writeUnauthorized(response, "令牌无效或已过期");
    }

    // 3. 用户状态
    SysUser user = userMapper.selectById(payload.userId());
    if (user == null) {
      return writeUnauthorized(response, "用户不存在");
    }
    if (!user.isEnabled()) {
      return writeUnauthorized(response, "账号已被禁用");
    }

    // 4. 构建主体并写入上下文
    AuthContext.set(new LoginUser(user.getId(), user.getUsername(), user.getNickname()));
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) {
    AuthContext.clear();
  }

  private boolean isWhiteList(String uri) {
    for (String prefix : tokenProperties.getSecurity().getWhiteList()) {
      if (uri.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }

  private boolean writeUnauthorized(HttpServletResponse response, String message) throws Exception {
    // 返回真实 HTTP 401，vben 前端据此触发重新认证/刷新令牌
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return writeResult(response, Result.fail(ResultCode.UNAUTHORIZED, message));
  }

  private boolean writeResult(HttpServletResponse response, Result<?> result) throws Exception {
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(result));
    return false;
  }
}
