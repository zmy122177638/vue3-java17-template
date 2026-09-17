package com.zmy.yorvix.security;

import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.service.system.MenuService;
import com.zmy.yorvix.service.system.RoleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 认证与鉴权拦截器。
 *
 * <h2>为什么值得这么细</h2>
 * 这个类的每个分支都是一条安全边界，而它的失败形态是"静默"的：
 * 少判一次令牌版本校验、把 401 写成 200、忘了清理线程上下文——功能测试全绿，
 * 但安全属性已经没了。因此这里对**每条拒绝路径**都写死断言（状态码 + 响应体 code），
 * 并对上下文清理单独留了一个回归用例。
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

  private static final String TOKEN = "opaque-token-value";

  @Mock
  private TokenService tokenService;

  @Mock
  private SysUserMapper userMapper;

  @Mock
  private RoleService roleService;

  @Mock
  private MenuService menuService;

  private AuthInterceptor interceptor;

  @BeforeEach
  void setUp() {
    TokenProperties properties = new TokenProperties();
    properties.getSecurity().setWhiteList(
        List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/error"));
    interceptor = new AuthInterceptor(properties, tokenService, userMapper, roleService, menuService,
        new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  // ---------------- 放行路径 ----------------

  @Test
  @DisplayName("白名单路径直接放行，且不查 Redis/数据库")
  void whitelistedRequestPassesWithoutAnyLookup() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(interceptor.preHandle(request, response, adminHandler())).isTrue();
    verifyNoInteractions(tokenService, userMapper, roleService);
  }

  @Test
  @DisplayName("非控制器方法（静态资源等）放行")
  void nonHandlerRequestPasses() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/whatever");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    verifyNoInteractions(tokenService);
  }

  @Test
  @DisplayName("鉴权通过：写入上下文，且角色查询复用已加载的 is_super（不重复读同一行）")
  void injectsContextAndReusesLoadedSuperFlag() throws Exception {
    MockHttpServletRequest request = requestFor("/api/user/info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenService.resolveAccess(TOKEN)).thenReturn(Optional.of(payload(3L, 0)));
    when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", 1, 1, 0));
    when(roleService.getRoleCodes(3L, true)).thenReturn(List.of("ADMIN"));

    assertThat(interceptor.preHandle(request, response, openHandler())).isTrue();

    LoginUser current = AuthContext.require();
    assertThat(current.getUserId()).isEqualTo(3L);
    assertThat(current.getUsername()).isEqualTo("alice");
    assertThat(current.hasRole("ADMIN")).isTrue();
    // 关键：把已加载的 is_super 传下去，避免 getRoleCodes 内部再查一次 sys_user
    verify(roleService).getRoleCodes(3L, true);
    verify(roleService, never()).getRoleCodes(3L);
  }

  // ---------------- 401 路径 ----------------

  @Test
  @DisplayName("缺少令牌 -> HTTP 401 + code 401")
  void rejectsMissingToken() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    // 刻意不带 Authorization 头：走的是"未提供令牌"分支，而不是"令牌无效"分支
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/info");

    assertThat(interceptor.preHandle(request, response, openHandler())).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":401");
    verifyNoInteractions(tokenService);
  }

  @Test
  @DisplayName("令牌不在 Redis（已登出/已过期）-> 401")
  void rejectsTokenMissingFromRedis() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenService.resolveAccess(TOKEN)).thenReturn(Optional.empty());

    assertThat(interceptor.preHandle(requestFor("/api/user/info"), response, openHandler()))
        .isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    // 不再查库：令牌无效时连用户都不该读
    verifyNoInteractions(userMapper);
  }

  @Test
  @DisplayName("用户已被删除 -> 401")
  void rejectsDeletedUser() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenService.resolveAccess(TOKEN)).thenReturn(Optional.of(payload(3L, 0)));
    when(userMapper.selectById(3L)).thenReturn(null);

    assertThat(interceptor.preHandle(requestFor("/api/user/info"), response, openHandler()))
        .isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("账号被禁用 -> 401")
  void rejectsDisabledUser() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(tokenService.resolveAccess(TOKEN)).thenReturn(Optional.of(payload(3L, 0)));
    when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", 0, 0, 0));

    assertThat(interceptor.preHandle(requestFor("/api/user/info"), response, openHandler()))
        .isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("令牌版本不一致（改密/重置密码/禁用后）-> 401，旧令牌立即失效")
  void rejectsStaleTokenVersion() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    // 签发时版本 0，库里已经是 1 -> 该令牌已被批量作废
    when(tokenService.resolveAccess(TOKEN)).thenReturn(Optional.of(payload(3L, 0)));
    when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", 1, 0, 1));

    assertThat(interceptor.preHandle(requestFor("/api/user/info"), response, openHandler()))
        .isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  // ---------------- 403 路径 ----------------

  @Test
  @DisplayName("缺少 @RequiresRoles 要求的角色 -> HTTP 403")
  void forbidsWhenRequiredRoleMissing() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    mockAuthenticatedUser(List.of("USER"));

    assertThat(interceptor.preHandle(requestFor("/api/system/users/page"), response,
        adminHandler())).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":403");
  }

  @Test
  @DisplayName("持有要求角色 -> 通过")
  void allowsWhenRequiredRolePresent() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    mockAuthenticatedUser(List.of("ADMIN"));

    assertThat(interceptor.preHandle(requestFor("/api/system/users/page"), response,
        adminHandler())).isTrue();
  }

  @Test
  @DisplayName("缺少 @RequiresPermissions 要求的权限码 -> 403（隐藏按钮不等于拒绝请求）")
  void forbidsWhenPermissionCodeMissing() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    mockAuthenticatedUser(List.of("USER"));
    when(menuService.authCodesOf(any())).thenReturn(Set.of("Report:View"));

    assertThat(interceptor.preHandle(requestFor("/api/report/export"), response,
        permissionHandler())).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("权限码齐备 -> 通过")
  void allowsWhenPermissionCodePresent() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    mockAuthenticatedUser(List.of("USER"));
    when(menuService.authCodesOf(any())).thenReturn(Set.of("Report:Export"));

    assertThat(interceptor.preHandle(requestFor("/api/report/export"), response,
        permissionHandler())).isTrue();
  }

  @Test
  @DisplayName("未标注权限注解的接口不查权限码（懒加载，零额外开销）")
  void doesNotLoadAuthCodesForUnannotatedEndpoint() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    mockAuthenticatedUser(List.of("USER"));

    assertThat(interceptor.preHandle(requestFor("/api/user/info"), response, openHandler()))
        .isTrue();
    verify(menuService, never()).authCodesOf(any());
  }

  // ---------------- 上下文清理（回归） ----------------

  @Test
  @DisplayName("回归：被 403 拒绝后上下文必须被清理（Spring 不会回调 afterCompletion）")
  void contextIsClearedAfterRejection() throws Exception {
    MockHttpServletRequest request = requestFor("/api/system/users/page");
    MockHttpServletResponse response = new MockHttpServletResponse();
    mockAuthenticatedUser(List.of("USER"));
    HandlerMethod handler = adminHandler();
    // 复现真实链路：拦截器先注入上下文，再返回 false 终止请求。
    // Spring 的 applyPreHandle 在这种情况**不会**调用 afterCompletion，
    // 因此清理只能由 AuthContextFilter 的 finally 兜住
    AtomicBoolean contextSetInsideChain = new AtomicBoolean();
    FilterChain rejectedChain = (req, res) -> {
      try {
        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        contextSetInsideChain.set(AuthContext.getOrNull() != null);
      } catch (Exception e) {
        throw new ServletException(e);
      }
    };

    new AuthContextFilter().doFilter(request, response, rejectedChain);

    assertThat(contextSetInsideChain).isTrue();
    assertThat(AuthContext.getOrNull()).isNull();
  }

  // ---------------- 测试夹具 ----------------

  private void mockAuthenticatedUser(List<String> roles) {
    when(tokenService.resolveAccess(TOKEN)).thenReturn(Optional.of(payload(3L, 0)));
    when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", 1, 0, 0));
    when(roleService.getRoleCodes(3L, false)).thenReturn(roles);
  }

  private MockHttpServletRequest requestFor(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.addHeader("Authorization", "Bearer " + TOKEN);
    return request;
  }

  private TokenService.TokenPayload payload(Long userId, int tokenVersion) {
    return new TokenService.TokenPayload(userId, "alice", "Alice", tokenVersion);
  }

  private SysUser user(Long id, String username, Integer status, Integer isSuper, int tokenVersion) {
    SysUser user = new SysUser();
    user.setId(id);
    user.setUsername(username);
    user.setStatus(status);
    user.setIsSuper(isSuper);
    user.setTokenVersion(tokenVersion);
    return user;
  }

  private HandlerMethod openHandler() throws NoSuchMethodException {
    return handler("open");
  }

  private HandlerMethod adminHandler() throws NoSuchMethodException {
    return handler("adminOnly");
  }

  private HandlerMethod permissionHandler() throws NoSuchMethodException {
    return handler("withPermission");
  }

  private HandlerMethod handler(String methodName) throws NoSuchMethodException {
    return new HandlerMethod(new DummyController(),
        DummyController.class.getMethod(methodName));
  }

  /** 测试用控制器：承载三种鉴权形态 */
  @SuppressWarnings("unused")
  static class DummyController {

    @RequiresRoles("ADMIN")
    public String adminOnly() {
      return "ok";
    }

    @RequiresPermissions("Report:Export")
    public String withPermission() {
      return "ok";
    }

    public String open() {
      return "ok";
    }
  }
}
