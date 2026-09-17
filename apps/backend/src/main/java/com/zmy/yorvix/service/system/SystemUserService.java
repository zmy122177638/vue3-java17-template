package com.zmy.yorvix.service.system;

import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.request.system.UserCreateRequest;
import com.zmy.yorvix.request.system.UserUpdateRequest;
import com.zmy.yorvix.response.system.RoleItem;
import com.zmy.yorvix.response.system.UserItem;

import java.util.List;

/**
 * 系统用户管理服务（管理员专用）。
 * <p>operatorId 为当前操作者，用于越权校验（ADMIN 角色仅超管可分配、
 * 管理员账号仅超管可操作），服务层不依赖线程上下文以便测试。
 */
public interface SystemUserService {

  PageResult<UserItem> page(PageQuery pageQuery, String keyword, Integer status, Long operatorId);

  boolean create(UserCreateRequest request, Long operatorId);

  boolean update(Long id, UserUpdateRequest request, Long operatorId);

  boolean updateStatus(Long id, Integer status, Long operatorId);

  boolean resetPassword(Long id, String rawPassword, Long operatorId);

  /** 删除用户（不允许删除自己、内置 admin 账号与管理员账号） */
  boolean delete(Long id, Long operatorId);

  /** 用户已分配角色 ID（编辑回显） */
  List<Long> getRoleIds(Long userId);

  /** 当前操作者可分配的角色（非超管不含内置 ADMIN 角色，仅启用角色） */
  List<RoleItem> assignableRoles(Long operatorId);
}
