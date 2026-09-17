package com.zmy.yorvix.controller.system;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.request.system.MenuSortRequest;
import com.zmy.yorvix.request.system.MenuUpsertRequest;
import com.zmy.yorvix.response.system.MenuNode;
import com.zmy.yorvix.security.RequiresRoles;
import com.zmy.yorvix.service.system.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理接口（仅管理员）。
 */
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@RequiresRoles("ADMIN")
public class SystemMenuController {

  private final MenuService menuService;

  /** 全量菜单树（含按钮、含禁用项，供授权树与列表展示） */
  @GetMapping("/tree")
  public Result<List<MenuNode>> tree() {
    return Result.ok(menuService.getFullTree());
  }

  @PostMapping
  public Result<Boolean> create(@Valid @RequestBody MenuUpsertRequest request) {
    return Result.ok(menuService.createMenu(request));
  }

  @PutMapping("/{id}")
  public Result<Boolean> update(@PathVariable Long id,
      @Valid @RequestBody MenuUpsertRequest request) {
    return Result.ok(menuService.updateMenu(id, request));
  }

  /** 同级拖拽排序：按 ids 顺序重排 pid 下的兄弟节点 */
  @PutMapping("/sort")
  public Result<Boolean> sort(@Valid @RequestBody MenuSortRequest request) {
    return Result.ok(menuService.sortMenus(request));
  }

  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable Long id) {
    return Result.ok(menuService.deleteMenu(id));
  }
}
