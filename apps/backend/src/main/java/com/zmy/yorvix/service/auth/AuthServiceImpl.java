package com.zmy.yorvix.service.auth;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.request.auth.ChangePasswordRequest;
import com.zmy.yorvix.request.auth.LoginRequest;
import com.zmy.yorvix.response.auth.LoginUserInfo;
import com.zmy.yorvix.response.auth.LoginResponse;
import com.zmy.yorvix.security.AuthContext;
import com.zmy.yorvix.security.ClientInfo;
import com.zmy.yorvix.security.LoginAttemptGuard;
import com.zmy.yorvix.security.LoginUser;
import com.zmy.yorvix.security.PasswordEncoder;
import com.zmy.yorvix.security.TokenService;
import com.zmy.yorvix.service.system.AuditLogService;
import com.zmy.yorvix.service.system.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务实现：登录、登出（删除 Redis 令牌）、刷新（令牌轮换）、当前用户、改密。
 *
 * <h2>登录为什么**不加** {@code @Transactional}</h2>
 * 失败路径上要写两条不能被回滚的痕迹：Redis 里的失败计数（本来就不受事务影响）
 * 与 {@code sys_login_log}。如果登录包在事务里，抛出的 {@code BizException} 会把
 * 登录日志一起回滚——审计表将只剩成功记录，恰好丢掉最需要的部分。
 * 同时这也避免"在事务中做十万次 PBKDF2 迭代"把数据库连接长期占住。
 *
 * <h2>登录失败为什么不区分"账号不存在"与"密码错误"</h2>
 * 两者返回同一个 {@link ResultCode#USERNAME_OR_PASSWORD_ERROR}，且**账号不存在时
 * 也走一次等价的散列计算**（{@link #dummyPasswordHash()}），否则响应时间差异
 * 就足以被用来枚举有效账号。
 * <p>"账号被禁用"的提示放在口令校验**之后**：那时调用方已经证明自己知道口令，
 * 提示不会泄露额外信息，却能让合法用户直接明白问题所在（而不是反复试密码）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final SysUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final TokenProperties tokenProperties;
  private final RoleService roleService;
  private final LoginAttemptGuard loginAttemptGuard;
  private final AuditLogService auditLogService;

  /**
   * 账号不存在时用于"等时比较"的哑散列（惰性生成一次）。
   * <p>内容随机、不可能被匹配，唯一作用是让"账号不存在"这条路径也付出与真实校验
   * 相同量级的计算代价。惰性生成是为了不拖慢启动与纯逻辑单测。
   */
  private volatile String dummyPasswordHash;

  @Override
  public LoginResponse login(LoginRequest request, ClientInfo client) {
    String username = request.getUsername();
    // 1. 限流必须在查库/校验口令之前：放到后面等于没防住"用少量请求打满 CPU"
    try {
      loginAttemptGuard.checkAllowed(username, client.ip());
    } catch (BizException e) {
      // 被限流的尝试也要留痕：这是"有人在撞库"最直接的信号，比单条失败更有研判价值。
      // userId 故意留空而不是再查一次库——限流路径上不能再产生额外查询。
      // 用**不带占位符**的审计专用 key：审计表只存 key、无法持久化"剩余分钟数"参数，
      // 若直接用 error.login.locked，列表解析后会残留 {0}
      auditLogService.recordLogin(username, null, AuditLogService.LoginEvent.LOCKED,
          "error.login.locked.audit", client);
      throw e;
    }

    // 2. 查账号 + 校验口令合并为一个判定；账号不存在时用哑散列补齐计算耗时，
    //    使"存在/不存在"在响应时间上不可区分
    SysUser user = userMapper.findByUsername(username).orElse(null);
    boolean passwordMatched = passwordEncoder.matches(request.getPassword(),
        user == null ? dummyPasswordHash() : user.getPasswordHash());
    if (user == null || !passwordMatched) {
      loginAttemptGuard.recordFailure(username, client.ip());
      auditLogService.recordLogin(username, user == null ? null : user.getId(),
          AuditLogService.LoginEvent.FAILURE,
          ResultCode.USERNAME_OR_PASSWORD_ERROR.getMessageKey(), client);
      throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
    }

    // 3. 禁用判定放在口令校验之后（理由见类注释）
    if (!user.isEnabled()) {
      auditLogService.recordLogin(username, user.getId(), AuditLogService.LoginEvent.DISABLED,
          ResultCode.ACCOUNT_DISABLED.getMessageKey(), client);
      throw new BizException(ResultCode.ACCOUNT_DISABLED);
    }

    loginAttemptGuard.reset(username, client.ip());
    auditLogService.recordLogin(username, user.getId(), AuditLogService.LoginEvent.SUCCESS, null,
        client);
    return issueTokens(user);
  }

  @Override
  public void logout(String token, ClientInfo client) {
    // 先解析载荷拿到操作人：revoke 之后就查不到了，日志会退化成"匿名登出"
    TokenService.TokenPayload payload = tokenService.resolveAccess(token).orElse(null);
    // Opaque Token：登出 = 删除 Redis 中的令牌，立即失效（幂等）
    tokenService.revokeAccess(token);
    auditLogService.recordLogin(payload == null ? null : payload.username(),
        payload == null ? null : payload.userId(), AuditLogService.LoginEvent.LOGOUT, null, client);
  }

  @Override
  public LoginResponse refresh(String token) {
    if (!StringUtils.hasText(token)) {
      throw new BizException(ResultCode.TOKEN_INVALID);
    }
    TokenService.TokenPayload payload = tokenService.resolveRefresh(token)
        .orElseThrow(() -> new BizException(ResultCode.TOKEN_INVALID));
    SysUser user = Optional.ofNullable(userMapper.selectById(payload.userId()))
        .orElseThrow(() -> new BizException(ResultCode.TOKEN_INVALID));
    if (!user.isEnabled()) {
      throw new BizException(ResultCode.ACCOUNT_DISABLED);
    }
    // 令牌版本必须一致，否则改密/重置密码/禁用后仍能用旧 refresh token 换出新 access token
    if (!user.matchesTokenVersion(payload.tokenVersion())) {
      // 该令牌已永久失效，顺手删除以免占着 Redis 到 TTL（refresh 默认 7 天）过期。
      // 拦截器侧的 access token 不做同样处理：它的 TTL 短，且那是每次请求都会走的热路径
      tokenService.revokeRefresh(token);
      throw new BizException(ResultCode.TOKEN_INVALID);
    }
    // 令牌轮换：旧 refresh token 立即失效，签发新的 access + refresh token
    tokenService.revokeRefresh(token);
    return issueTokens(user);
  }

  @Override
  @Transactional(readOnly = true)
  public LoginUserInfo me() {
    return toInfo(AuthContext.require());
  }

  @Override
  @Transactional
  public void changePassword(ChangePasswordRequest request) {
    LoginUser current = AuthContext.require();
    SysUser user = userMapper.findByUsername(current.getUsername())
        .orElseThrow(() -> new BizException(ResultCode.USER_NOT_FOUND));
    if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
      throw new BizException(ResultCode.PASSWORD_ERROR);
    }
    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    userMapper.updateById(user);
    // 改密后踢下线：递增令牌版本，使当前会话与其它设备上的会话一并失效。
    // 账号被攻陷时这一步是必需的——只改密码而不作废旧会话，攻击者仍能用旧令牌继续访问
    userMapper.bumpTokenVersion(user.getId());
    log.info("用户 {} 修改了密码，已使其全部会话失效", user.getUsername());
  }

  /** 签发新的 access + refresh token 对 */
  private LoginResponse issueTokens(SysUser user) {
    // 复用已加载的 user 判定超管，避免 getRoleCodes 内部再读一次 sys_user
    List<String> roles = roleService.getRoleCodes(user.getId(), user.isSuperUser());
    LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), roles,
        user.isSuperUser());
    int tokenVersion = user.tokenVersionOrDefault();
    String accessToken = tokenService.createAccessToken(
        loginUser.getUserId(), loginUser.getUsername(), loginUser.getNickname(), tokenVersion);
    String refreshToken = tokenService.createRefreshToken(
        loginUser.getUserId(), loginUser.getUsername(), loginUser.getNickname(), tokenVersion);
    return LoginResponse.of(accessToken, refreshToken,
        tokenProperties.getExpireMinutes() * 60, toInfo(loginUser));
  }

  private LoginUserInfo toInfo(LoginUser loginUser) {
    LoginUserInfo info = new LoginUserInfo();
    info.setUserId(loginUser.getUserId());
    info.setUsername(loginUser.getUsername());
    info.setNickname(loginUser.getNickname());
    return info;
  }

  /**
   * 账号不存在时用于等时比较的哑散列。
   * <p>内容随机（含 UUID）因此永远不可能被匹配成功；散列参数与真实口令一致
   * （同一个 {@link PasswordEncoder}，迭代次数取自其常量），所以耗时同量级。
   * <p>用双重检查的惰性初始化而不是 {@code @PostConstruct}：少一次启动期开销，
   * 也让不涉及登录的单元测试不必走这一步。
   */
  private String dummyPasswordHash() {
    String cached = dummyPasswordHash;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (dummyPasswordHash == null) {
        dummyPasswordHash = passwordEncoder.encode("dummy-" + UUID.randomUUID());
      }
      return dummyPasswordHash;
    }
  }
}
