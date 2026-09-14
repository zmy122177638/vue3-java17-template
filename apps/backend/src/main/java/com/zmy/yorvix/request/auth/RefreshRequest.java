package com.zmy.yorvix.request.auth;

import lombok.Getter;
import lombok.Setter;

/**
 * 刷新令牌请求。
 * <p>vben 前端调用 refresh 时不携带 token 字段（走 HttpOnly Cookie），
 * 因此 token 允许为空，由 Controller 回退到 Cookie 取值。
 */
@Getter
@Setter
public class RefreshRequest {

  /** 可选：请求体中的 refresh token（便于非浏览器/测试场景直连调用） */
  private String token;
}
