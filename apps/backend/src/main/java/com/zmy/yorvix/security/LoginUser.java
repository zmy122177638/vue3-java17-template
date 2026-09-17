package com.zmy.yorvix.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 当前登录用户（线程上下文主体），含基础身份与角色编码列表。
 * <p>roles 由 AuthInterceptor 在每次请求时查库注入，角色变更即时生效。
 */
@Getter
@AllArgsConstructor
public class LoginUser {

  private final Long userId;
  private final String username;
  private final String nickname;

  /** 角色编码列表（如 ADMIN），可能为空列表 */
  private final List<String> roles;

  /** 是否超级管理员（仅种子/改库维护的账号） */
  private final boolean superUser;

  /** 是否持有指定全部角色 */
  public boolean hasAllRoles(List<String> expected) {
    return roles != null && roles.containsAll(expected);
  }

  /**
   * 是否持有指定角色。
   * <p>业务代码优先用这个判断管理员身份，而不是再查一次库：
   * roles 已由 {@code AuthInterceptor} 在每个请求入口注入，语义与 {@code RoleService#isAdmin} 等价
   * （超管在注入时已补上 ADMIN 编码）。
   */
  public boolean hasRole(String code) {
    return roles != null && roles.contains(code);
  }
}
