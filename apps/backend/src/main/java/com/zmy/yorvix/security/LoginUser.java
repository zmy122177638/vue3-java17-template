package com.zmy.yorvix.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前登录用户（线程上下文主体），仅含基础身份信息。
 */
@Getter
@AllArgsConstructor
public class LoginUser {

  private final Long userId;
  private final String username;
  private final String nickname;
}
