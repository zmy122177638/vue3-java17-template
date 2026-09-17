package com.zmy.yorvix.service.auth;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.request.auth.ChangePasswordRequest;
import com.zmy.yorvix.request.auth.LoginRequest;
import com.zmy.yorvix.response.auth.LoginResponse;
import com.zmy.yorvix.security.AuthContext;
import com.zmy.yorvix.security.ClientInfo;
import com.zmy.yorvix.security.LoginAttemptGuard;
import com.zmy.yorvix.security.LoginUser;
import com.zmy.yorvix.security.PasswordEncoder;
import com.zmy.yorvix.security.TokenService;
import com.zmy.yorvix.service.system.AuditLogService;
import com.zmy.yorvix.service.system.AuditLogService.LoginEvent;
import com.zmy.yorvix.service.system.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 认证服务：登录、限流、账号枚举防护、刷新轮换、改密踢下线。
 *
 * <h2>这份测试在守什么</h2>
 * 认证是"错了就是安全事故"的地方，而它的错误形态往往不报错、只是行为微妙地不对：
 * <ul>
 *   <li><b>账号枚举</b>：密码错与账号不存在的返回必须完全一致，
 *       并且账号不存在时也要付出等价的散列计算；</li>
 *   <li><b>禁用状态泄露</b>：只有口令正确时才允许提示"账号被禁用"；</li>
 *   <li><b>限流前置</b>：被锁定时不能再去查库/算散列，否则限流挡不住 CPU 消耗；</li>
 *   <li><b>令牌轮换</b>：refresh 必须让旧 refresh token 立即失效；</li>
 *   <li><b>改密踢下线</b>：必须递增 token_version。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  private static final String IP = "10.0.0.1";
  private static final String DUMMY_HASH = "pbkdf2$100000$dummy$dummy";

  @Mock
  private SysUserMapper userMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private TokenService tokenService;

  @Mock
  private RoleService roleService;

  @Mock
  private LoginAttemptGuard loginAttemptGuard;

  @Mock
  private AuditLogService auditLogService;

  private AuthServiceImpl authService;

  private final ClientInfo client = new ClientInfo(IP, "JUnit");

  @BeforeEach
  void setUp() {
    authService = new AuthServiceImpl(userMapper, passwordEncoder, tokenService,
        new TokenProperties(), roleService, loginAttemptGuard, auditLogService);
    // 账号不存在时用它做等时比较，这里固定返回值便于断言"确实走了这次计算"
    lenient().when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  // ---------------- 登录成功 ----------------

  @Test
  @DisplayName("登录成功：先限流校验，签发令牌，清零失败计数，写成功审计")
  void loginIssuesTokensAndResetsFailureCounter() {
    when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice", 1, 0, 0)));
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(roleService.getRoleCodes(1L, false)).thenReturn(List.of("USER"));
    when(tokenService.createAccessToken(1L, "alice", null, 0)).thenReturn("access-token");
    when(tokenService.createRefreshToken(1L, "alice", null, 0)).thenReturn("refresh-token");

    LoginResponse response = authService.login(loginRequest("alice", "secret"), client);

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    verify(loginAttemptGuard).checkAllowed("alice", IP);
    verify(loginAttemptGuard).reset("alice", IP);
    verify(auditLogService).recordLogin("alice", 1L, LoginEvent.SUCCESS, null, client);
  }

  // ---------------- 账号枚举防护 ----------------

  @Test
  @DisplayName("账号不存在：返回与密码错误相同的码，且**同样执行一次散列计算**（消除时序差异）")
  void unknownUsernameIsIndistinguishableFromWrongPassword() {
    when(userMapper.findByUsername("ghost")).thenReturn(Optional.empty());

    BizException thrown = catchThrowableOfType(
        () -> authService.login(loginRequest("ghost", "secret"), client), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode());
    // 关键：用的是哑散列（如果直接跳过计算，响应时间就会暴露"这个账号不存在"）
    verify(passwordEncoder).matches("secret", DUMMY_HASH);
    verify(loginAttemptGuard).recordFailure("ghost", IP);
    verify(auditLogService).recordLogin("ghost", null, LoginEvent.FAILURE,
        ResultCode.USERNAME_OR_PASSWORD_ERROR.getMessageKey(), client);
  }

  @Test
  @DisplayName("密码错误：返回码与账号不存在完全一致")
  void wrongPasswordReturnsSameCodeAsUnknownUsername() {
    when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice", 1, 0, 0)));
    when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

    BizException thrown = catchThrowableOfType(
        () -> authService.login(loginRequest("alice", "bad"), client), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode());
    // 真实账号上用真实散列，而不是哑散列
    verify(passwordEncoder).matches("bad", "hash");
    verify(loginAttemptGuard).recordFailure("alice", IP);
  }

  @Test
  @DisplayName("禁用账号 + 密码错误：不能提示'已禁用'，否则不用口令就能枚举账号状态")
  void disabledAccountIsNotRevealedWhenPasswordIsWrong() {
    when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice", 0, 0, 0)));
    when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

    BizException thrown = catchThrowableOfType(
        () -> authService.login(loginRequest("alice", "bad"), client), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode());
  }

  @Test
  @DisplayName("禁用账号 + 密码正确：此时才提示'账号已被禁用'（调用方已证明自己知道口令）")
  void disabledAccountIsRevealedAfterCorrectPassword() {
    when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice", 0, 0, 0)));
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

    BizException thrown = catchThrowableOfType(
        () -> authService.login(loginRequest("alice", "secret"), client), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.ACCOUNT_DISABLED.getCode());
    // 不签发令牌、也不清零失败计数
    verifyNoInteractions(tokenService);
    verify(loginAttemptGuard, never()).reset(anyString(), anyString());
  }

  // ---------------- 限流 ----------------

  @Test
  @DisplayName("已被限流时直接拒绝，不查库、不算散列（否则限流挡不住 CPU 消耗）")
  void lockedOutRequestTouchesNothing() {
    doThrow(new BizException(ResultCode.LOGIN_LOCKED, ResultCode.LOGIN_LOCKED.getMessageKey(), 5L))
        .when(loginAttemptGuard).checkAllowed("alice", IP);

    BizException thrown = catchThrowableOfType(
        () -> authService.login(loginRequest("alice", "secret"), client), BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.LOGIN_LOCKED.getCode());
    // 被限流的尝试必须留痕（这是"有人在撞库"最直接的信号），且不能因此多查一次库。
    // 审计用的是**不带占位符**的专用 key：审计表只存 key、无法持久化"剩余分钟数"参数，
    // 若沿用 error.login.locked，日志列表解析后会残留 {0}
    verify(auditLogService).recordLogin("alice", null, LoginEvent.LOCKED,
        "error.login.locked.audit", client);
    verifyNoInteractions(userMapper, passwordEncoder, tokenService);
  }

  // ---------------- 刷新 ----------------

  @Test
  @DisplayName("刷新成功：轮换 refresh token（旧的立即失效）并签发新令牌对")
  void refreshRotatesRefreshToken() {
    when(tokenService.resolveRefresh("old-refresh"))
        .thenReturn(Optional.of(payload(1L, 0)));
    when(userMapper.selectById(1L)).thenReturn(user(1L, "alice", 1, 0, 0));
    when(roleService.getRoleCodes(1L, false)).thenReturn(List.of("USER"));
    when(tokenService.createAccessToken(1L, "alice", null, 0)).thenReturn("new-access");
    when(tokenService.createRefreshToken(1L, "alice", null, 0)).thenReturn("new-refresh");

    LoginResponse response = authService.refresh("old-refresh");

    assertThat(response.getAccessToken()).isEqualTo("new-access");
    assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    verify(tokenService).revokeRefresh("old-refresh");
  }

  @Test
  @DisplayName("令牌版本不一致（改密/重置密码/禁用后）：refresh 也必须拒绝，并顺手删掉旧 key")
  void refreshRejectsStaleTokenVersion() {
    when(tokenService.resolveRefresh("old-refresh"))
        .thenReturn(Optional.of(payload(1L, 0)));
    // 库里版本已经是 1，说明该令牌已被批量作废
    when(userMapper.selectById(1L)).thenReturn(user(1L, "alice", 1, 0, 1));

    BizException thrown = catchThrowableOfType(() -> authService.refresh("old-refresh"),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.TOKEN_INVALID.getCode());
    verify(tokenService).revokeRefresh("old-refresh");
    // 关键：不能借旧 refresh token 换出新的 access token
    verify(tokenService, never()).createAccessToken(any(), anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("账号被禁用：refresh 直接拒绝")
  void refreshRejectsDisabledUser() {
    when(tokenService.resolveRefresh("old-refresh"))
        .thenReturn(Optional.of(payload(1L, 0)));
    when(userMapper.selectById(1L)).thenReturn(user(1L, "alice", 0, 0, 0));

    BizException thrown = catchThrowableOfType(() -> authService.refresh("old-refresh"),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.ACCOUNT_DISABLED.getCode());
  }

  @Test
  @DisplayName("空 refresh token：直接 TOKEN_INVALID，不查 Redis")
  void refreshRejectsBlankToken() {
    BizException thrown = catchThrowableOfType(() -> authService.refresh("  "),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.TOKEN_INVALID.getCode());
    verifyNoInteractions(tokenService);
  }

  // ---------------- 登出 ----------------

  @Test
  @DisplayName("登出：删除 access token 并留下可追溯到人的审计记录")
  void logoutRevokesAccessTokenAndAudits() {
    when(tokenService.resolveAccess("access"))
        .thenReturn(Optional.of(payload(1L, 0)));

    authService.logout("access", client);

    verify(tokenService).revokeAccess("access");
    verify(auditLogService).recordLogin("alice", 1L, LoginEvent.LOGOUT, null, client);
  }

  @Test
  @DisplayName("登出时令牌已失效：仍然是幂等的，只是审计里没有操作人")
  void logoutIsIdempotentWhenTokenAlreadyGone() {
    when(tokenService.resolveAccess("stale")).thenReturn(Optional.empty());

    authService.logout("stale", client);

    verify(tokenService).revokeAccess("stale");
    verify(auditLogService).recordLogin(null, null, LoginEvent.LOGOUT, null, client);
  }

  // ---------------- 改密 ----------------

  @Test
  @DisplayName("修改密码：原密码错误只报错，不做任何写操作")
  void changePasswordRejectsWrongOldPassword() {
    AuthContext.set(new LoginUser(1L, "alice", "Alice", List.of("ADMIN"), false));
    when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice", 1, 0, 0)));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    ChangePasswordRequest request = new ChangePasswordRequest();
    request.setOldPassword("wrong");
    request.setNewPassword("new-secret");

    BizException thrown = catchThrowableOfType(() -> authService.changePassword(request),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.PASSWORD_ERROR.getCode());
    verify(userMapper, never()).updateById(any(SysUser.class));
    verify(userMapper, never()).bumpTokenVersion(any());
  }

  @Test
  @DisplayName("修改密码成功：写入新散列并递增令牌版本（当前会话与其它设备一并失效）")
  void changePasswordBumpsTokenVersionToKillAllSessions() {
    AuthContext.set(new LoginUser(1L, "alice", "Alice", List.of("ADMIN"), false));
    SysUser user = user(1L, "alice", 1, 0, 3);
    when(userMapper.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("old", "hash")).thenReturn(true);
    when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

    ChangePasswordRequest request = new ChangePasswordRequest();
    request.setOldPassword("old");
    request.setNewPassword("new-secret");
    authService.changePassword(request);

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    verify(userMapper).updateById(user);
    // 只改密码不作废旧会话 = 账号被攻陷后攻击者仍能继续用旧令牌
    verify(userMapper).bumpTokenVersion(1L);
  }

  // ---------------- 夹具 ----------------

  private LoginRequest loginRequest(String username, String password) {
    LoginRequest request = new LoginRequest();
    request.setUsername(username);
    request.setPassword(password);
    return request;
  }

  private TokenService.TokenPayload payload(Long userId, int tokenVersion) {
    return new TokenService.TokenPayload(userId, "alice", "Alice", tokenVersion);
  }

  private SysUser user(Long id, String username, Integer status, Integer isSuper, int tokenVersion) {
    SysUser user = new SysUser();
    user.setId(id);
    user.setUsername(username);
    user.setPasswordHash("hash");
    user.setStatus(status);
    user.setIsSuper(isSuper);
    user.setTokenVersion(tokenVersion);
    return user;
  }
}
