package com.zmy.yorvix.service.system;

import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.request.system.RoleUpsertRequest;
import com.zmy.yorvix.response.system.RoleItem;

import java.util.List;

/**
 * 系统角色管理服务（管理员专用）。ADMIN 为内置角色，禁止删除、code 不可修改。
 */
public interface SystemRoleService {

  PageResult<RoleItem> page(PageQuery pageQuery, String keyword, Integer status);

  boolean create(RoleUpsertRequest request);

  boolean update(Long id, RoleUpsertRequest request);

  /** 删除角色（内置 ADMIN 与仍被用户绑定的角色不可删） */
  boolean delete(Long id);

  /** 重置角色菜单授权（全量替换） */
  boolean saveMenus(Long id, List<Long> menuIds);

  /** 角色已授权菜单 ID 列表 */
  List<Long> getMenuIds(Long roleId);
}
