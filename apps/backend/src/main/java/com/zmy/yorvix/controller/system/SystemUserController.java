package com.zmy.yorvix.controller.system;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.page.PageQuery;
import com.zmy.yorvix.common.page.PageResult;
import com.zmy.yorvix.request.system.ResetPasswordRequest;
import com.zmy.yorvix.request.system.UserCreateRequest;
import com.zmy.yorvix.request.system.UserStatusRequest;
import com.zmy.yorvix.request.system.UserUpdateRequest;
import com.zmy.yorvix.response.system.RoleItem;
import com.zmy.yorvix.response.system.UserItem;
import com.zmy.yorvix.security.AuthContext;
import com.zmy.yorvix.security.RequiresRoles;
import com.zmy.yorvix.service.system.SystemUserService;
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
 * 用户管理接口（仅管理员）。
 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@RequiresRoles("ADMIN")
public class SystemUserController {

  private final SystemUserService userService;

  @GetMapping("/page")
  public Result<PageResult<UserItem>> page(PageQuery pageQuery,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer status) {
    return Result.ok(userService.page(pageQuery, keyword, status, currentUserId()));
  }

  @PostMapping
  public Result<Boolean> create(@Valid @RequestBody UserCreateRequest request) {
    return Result.ok(userService.create(request, currentUserId()));
  }

  @PutMapping("/{id}")
  public Result<Boolean> update(@PathVariable Long id,
      @Valid @RequestBody UserUpdateRequest request) {
    return Result.ok(userService.update(id, request, currentUserId()));
  }

  /** 仅切换状态（vben 列表开关） */
  @PutMapping("/{id}/status")
  public Result<Boolean> updateStatus(@PathVariable Long id,
      @Valid @RequestBody UserStatusRequest request) {
    return Result.ok(userService.updateStatus(id, request.getStatus(), currentUserId()));
  }

  /** 重置密码 */
  @PutMapping("/{id}/password")
  public Result<Boolean> resetPassword(@PathVariable Long id,
      @Valid @RequestBody ResetPasswordRequest request) {
    return Result.ok(userService.resetPassword(id, request.getPassword(), currentUserId()));
  }

  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable Long id) {
    return Result.ok(userService.delete(id, currentUserId()));
  }

  /** 用户已分配角色 ID（编辑回显） */
  @GetMapping("/{id}/role-ids")
  public Result<List<Long>> roleIds(@PathVariable Long id) {
    return Result.ok(userService.getRoleIds(id));
  }

  /**
   * 当前操作者可分配的角色列表（用户表单角色下拉用）。
   * <p>普通管理员不含内置 ADMIN 角色，因此界面上无法给他人授予管理员权限。
   */
  @GetMapping("/assignable-roles")
  public Result<List<RoleItem>> assignableRoles() {
    return Result.ok(userService.assignableRoles(currentUserId()));
  }

  private Long currentUserId() {
    return AuthContext.require().getUserId();
  }
}
