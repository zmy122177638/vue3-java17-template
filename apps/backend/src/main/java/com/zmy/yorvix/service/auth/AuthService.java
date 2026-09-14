package com.zmy.yorvix.service.auth;

import com.zmy.yorvix.request.auth.ChangePasswordRequest;
import com.zmy.yorvix.request.auth.LoginRequest;
import com.zmy.yorvix.response.auth.LoginUserInfo;
import com.zmy.yorvix.response.auth.LoginResponse;

/**
 * 认证服务接口：登录、登出（删除 Redis 令牌）、刷新（令牌轮换）、当前用户、改密。
 */
public interface AuthService {

  /** 登录，成功返回令牌与用户信息 */
  LoginResponse login(LoginRequest request);

  /** 登出：删除 access token 对应的 Redis 记录，使其立即失效（幂等） */
  void logout(String token);

  /** 刷新令牌（要求 refresh token 在 Redis 中有效），成功后轮换旧 refresh token */
  LoginResponse refresh(String token);

  /** 当前登录用户信息（含角色与权限） */
  LoginUserInfo me();

  /** 修改当前用户密码 */
  void changePassword(ChangePasswordRequest request);
}
