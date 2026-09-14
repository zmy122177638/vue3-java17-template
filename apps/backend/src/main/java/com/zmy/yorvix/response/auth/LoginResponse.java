package com.zmy.yorvix.response.auth;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录/刷新成功响应。
 * <p>refreshToken 用于服务端写入 HttpOnly Cookie（vben 前端刷新令牌走 Cookie）。
 */
@Getter
@Setter
public class LoginResponse {

  private String accessToken;
  private String refreshToken;
  private String tokenType = "Bearer";
  /** 有效期（秒） */
  private long expiresIn;
  private LoginUserInfo user;

  public static LoginResponse of(String accessToken, String refreshToken, long expiresIn,
      LoginUserInfo user) {
    LoginResponse resp = new LoginResponse();
    resp.setAccessToken(accessToken);
    resp.setRefreshToken(refreshToken);
    resp.setExpiresIn(expiresIn);
    resp.setUser(user);
    return resp;
  }
}
