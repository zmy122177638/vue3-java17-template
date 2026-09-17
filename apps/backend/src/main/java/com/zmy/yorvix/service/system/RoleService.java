package com.zmy.yorvix.service.system;

import java.util.List;

/**
 * 角色服务：角色编码查询、内置管理员判定、编码占用校验。
 */
public interface RoleService {

  /** 内置管理员角色编码（代码层全量放行，不可删除/改码） */
  String ADMIN_CODE = "ADMIN";

  /** 查询用户启用的角色编码列表（无角色返回空列表） */
  List<String> getRoleCodes(Long userId);

  /**
   * {@link #getRoleCodes(Long)} 的热点路径重载：调用方**已经加载过** {@code SysUser} 时，
   * 直接把超管标识传进来，省掉一次 {@code sys_user} 查询。
   * <p>为什么要这个重载：判定超管需要读 {@code sys_user}，而认证链路在入口本来就要读同一行
   * （校验状态与令牌版本）。不再复用就变成"同一个请求里把同一行读两次"——
   * 这是每个受保护请求都要付的固定开销，纯浪费。
   * <p>语义与 {@link #isSuper(Long)} 一致：{@code superUser=true} 时，即使没有绑定
   * ADMIN 角色也会补上该编码（超管代码层全量放行）。
   *
   * @param superUser 调用方已加载的 {@code sys_user.is_super} 标识
   */
  List<String> getRoleCodes(Long userId, boolean superUser);

  /** 是否内置管理员（持有 ADMIN 角色即视为管理员，全量放行；超管同样视为管理员） */
  boolean isAdmin(Long userId);

  /** 是否超级管理员（sys_user.is_super=1，仅种子/改库维护） */
  boolean isSuper(Long userId);

  /**
   * 是否存在角色绑定（**不区分角色启用/禁用**）。
   * <p>用于区分“压根没分配角色”和“分配了但角色已被禁用”两种无权限场景。
   */
  boolean hasRoleBinding(Long userId);

}
