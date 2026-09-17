package com.zmy.yorvix.controller.system;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.request.system.RoleMenusRequest;
import com.zmy.yorvix.request.system.RoleUpsertRequest;
import com.zmy.yorvix.response.system.RoleItem;
import com.zmy.yorvix.security.RequiresRoles;
import com.zmy.yorvix.service.system.SystemRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口（仅管理员）。
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@RequiresRoles("ADMIN")
public class SystemRoleController {

  private final SystemRoleService roleService;

  @GetMapping("/page")
  public Result<PageResult<RoleItem>> page(PageQuery pageQuery,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer status) {
    return Result.ok(roleService.page(pageQuery, keyword, status));
  }

  @PostMapping
  public Result<Boolean> create(@Valid @RequestBody RoleUpsertRequest request) {
    return Result.ok(roleService.create(request));
  }

  @PutMapping("/{id}")
  public Result<Boolean> update(@PathVariable Long id,
      @Valid @RequestBody RoleUpsertRequest request) {
    return Result.ok(roleService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable Long id) {
    return Result.ok(roleService.delete(id));
  }

  /** 角色已授权菜单 ID（授权树回显） */
  @GetMapping("/{id}/menu-ids")
  public Result<List<Long>> menuIds(@PathVariable Long id) {
    return Result.ok(roleService.getMenuIds(id));
  }

  /** 重置角色菜单授权 */
  @PutMapping("/{id}/menus")
  public Result<Boolean> saveMenus(@PathVariable Long id,
      @Valid @RequestBody RoleMenusRequest request) {
    return Result.ok(roleService.saveMenus(id, request.getMenuIds()));
  }
}
