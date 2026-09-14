package com.zmy.yorvix.service.auth;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.TokenProperties;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.request.auth.ChangePasswordRequest;
import com.zmy.yorvix.request.auth.LoginRequest;
import com.zmy.yorvix.response.auth.LoginUserInfo;
import com.zmy.yorvix.response.auth.LoginResponse;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.security.AuthContext;
import com.zmy.yorvix.security.LoginUser;
import com.zmy.yorvix.security.PasswordEncoder;
import com.zmy.yorvix.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 认证服务实现：登录、登出（删除 Redis 令牌）、刷新（令牌轮换）、当前用户、改密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final SysUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final TokenProperties tokenProperties;

  @Override
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    SysUser user = userMapper.findByUsername(request.getUsername())
        .orElseThrow(() -> new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR));
    if (!user.isEnabled()) {
      throw new BizException(ResultCode.ACCOUNT_DISABLED);
    }
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
    }
    return issueTokens(user);
  }

  @Override
  public void logout(String token) {
    // Opaque Token：登出 = 删除 Redis 中的令牌，立即失效（幂等）
    tokenService.revokeAccess(token);
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
    log.info("用户 {} 修改了密码", user.getUsername());
  }

  /** 签发新的 access + refresh token 对 */
  private LoginResponse issueTokens(SysUser user) {
    LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname());
    String accessToken = tokenService.createAccessToken(
        loginUser.getUserId(), loginUser.getUsername(), loginUser.getNickname());
    String refreshToken = tokenService.createRefreshToken(
        loginUser.getUserId(), loginUser.getUsername(), loginUser.getNickname());
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
}
