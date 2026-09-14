package com.zmy.yorvix.response.auth;

import lombok.Getter;
import lombok.Setter;

/**
 * 前端所需的登录用户信息（vben 契约；角色/权限由各项目自行扩展）。
 */
@Getter
@Setter
public class LoginUserInfo {

  private Long userId;
  private String username;
  private String nickname;
}
