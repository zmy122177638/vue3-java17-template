package com.zmy.yorvix.service.auth;

import com.zmy.yorvix.request.auth.ChangePasswordRequest;
import com.zmy.yorvix.request.auth.LoginRequest;
import com.zmy.yorvix.response.auth.LoginUserInfo;
import com.zmy.yorvix.response.auth.LoginResponse;
import com.zmy.yorvix.security.ClientInfo;

/**
 * 认证服务接口：登录、登出（删除 Redis 令牌）、刷新（令牌轮换）、当前用户、改密。
 * <p>登录/登出需要 {@link ClientInfo}：前者用于失败限流（按账号 + IP）与审计，
 * 后者仅用于审计。Servlet 相关的取值在 Controller 完成，本层只消费解析好的值。
 */
public interface AuthService {

  /**
   * 登录，成功返回令牌与用户信息。
   * <p>会先做失败限流（超限抛 {@code LOGIN_LOCKED}），失败会累计计数并写入登录日志。
   */
  LoginResponse login(LoginRequest request, ClientInfo client);

  /**
   * 登出：删除 access token 对应的 Redis 记录，使其立即失效（幂等），并写入登录日志。
   * <p>调用方必须把请求头中的 access token 传进来——只清前端状态而服务端令牌仍在
   * Redis 里有效，等于"登出后旧令牌还能用"，直到 TTL 结束。
   */
  void logout(String token, ClientInfo client);

  /** 刷新令牌（要求 refresh token 在 Redis 中有效），成功后轮换旧 refresh token */
  LoginResponse refresh(String token);

  /** 当前登录用户信息（含角色与权限） */
  LoginUserInfo me();

  /**
   * 修改当前用户密码。
   * <p>成功后会递增令牌版本，使该用户**所有会话**（含当前会话）立即失效，需用新密码重新登录。
   */
  void changePassword(ChangePasswordRequest request);
}
