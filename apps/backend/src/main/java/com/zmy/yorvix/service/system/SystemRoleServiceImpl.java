package com.zmy.yorvix.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.mapper.system.SysRoleMapper;
import com.zmy.yorvix.mapper.system.SysUserRoleMapper;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.system.SysUserRole;
import com.zmy.yorvix.request.system.RoleUpsertRequest;
import com.zmy.yorvix.response.system.RoleItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统角色管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class SystemRoleServiceImpl implements SystemRoleService {

  /** 列表允许的前端排序字段（白名单，理由见 SystemUserServiceImpl 同名常量） */
  private static final List<String> SORTABLE_FIELDS =
      List.of("code", "createdAt", "name", "status");

  private final SysRoleMapper roleMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final MenuService menuService;

  @Override
  @Transactional(readOnly = true)
  public PageResult<RoleItem> page(PageQuery pageQuery, String keyword, Integer status) {
    LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>();
    if (StringUtils.hasText(keyword)) {
      wrapper.and(w -> w.like(SysRole::getCode, keyword)
          .or().like(SysRole::getName, keyword));
    }
    if (status != null) {
      wrapper.eq(SysRole::getStatus, status);
    }
    Page<SysRole> page = roleMapper.selectPage(pageQuery.toMpPage(SORTABLE_FIELDS), wrapper);
    return PageResult.of(page, this::toItem);
  }

  @Override
  @Transactional
  public boolean create(RoleUpsertRequest request) {
    if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
        .eq(SysRole::getCode, request.getCode())) > 0) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.code.exists");
    }
    SysRole role = toRole(new SysRole(), request);
    roleMapper.insert(role);
    if (request.getMenuIds() != null) {
      menuService.replaceRoleMenus(role.getId(), request.getMenuIds());
    }
    return true;
  }

  @Override
  @Transactional
  public boolean update(Long id, RoleUpsertRequest request) {
    SysRole role = requireRole(id);
    // 内置管理员角色整体不可修改（含编码、名称、状态、授权），避免改动后管理员失效
    if (RoleService.ADMIN_CODE.equals(role.getCode())) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.builtin.update");
    }
    // 编码是 @RequiresRoles 与授权语义的锚点（改了会让既有鉴权/授权数据失效），
    // 且前端编辑态已把它置为只读，后端必须同强度约束，不能依赖"ADMIN 角色恰好存在"来间接拦截
    if (!role.getCode().equals(request.getCode())) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.code.immutable");
    }
    roleMapper.updateById(toRole(role, request));
    if (request.getMenuIds() != null) {
      menuService.replaceRoleMenus(id, request.getMenuIds());
    }
    return true;
  }

  @Override
  @Transactional
  public boolean delete(Long id) {
    SysRole role = requireRole(id);
    if (RoleService.ADMIN_CODE.equals(role.getCode())) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.builtin.delete");
    }
    long bound = userRoleMapper.selectCount(
        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    if (bound > 0) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.bound");
    }
    roleMapper.deleteById(id);
    return true;
  }

  @Override
  @Transactional
  public boolean saveMenus(Long id, List<Long> menuIds) {
    SysRole role = requireRole(id);
    // 内置管理员角色代码层全量放行、不读 sys_role_menu，写入授权只会产生无意义的脏数据
    if (RoleService.ADMIN_CODE.equals(role.getCode())) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.role.builtin.grant");
    }
    return menuService.replaceRoleMenus(id, menuIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> getMenuIds(Long roleId) {
    requireRole(roleId);
    return menuService.getMenuIdsByRoleId(roleId);
  }

  // ---------------- 内部 ----------------

  private SysRole requireRole(Long id) {
    SysRole role = roleMapper.selectById(id);
    if (role == null) {
      throw new BizException(ResultCode.NOT_FOUND, "error.role.not.found");
    }
    return role;
  }

  private SysRole toRole(SysRole role, RoleUpsertRequest request) {
    role.setCode(request.getCode());
    role.setName(StringUtils.hasText(request.getName()) ? request.getName() : request.getCode());
    role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    role.setRemark(request.getRemark());
    return role;
  }

  private RoleItem toItem(SysRole role) {
    RoleItem item = new RoleItem();
    item.setId(role.getId());
    item.setCode(role.getCode());
    item.setName(role.getName());
    item.setStatus(role.getStatus());
    item.setRemark(role.getRemark());
    item.setCreatedAt(role.getCreatedAt());
    return item;
  }
}
