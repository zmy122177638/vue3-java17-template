package com.zmy.yorvix.controller;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.request.auth.ProfileUpdateRequest;
import com.zmy.yorvix.response.auth.CurrentUserInfo;
import com.zmy.yorvix.response.auth.ProfileItem;
import com.zmy.yorvix.security.AuthContext;
import com.zmy.yorvix.security.LoginUser;
import com.zmy.yorvix.service.system.MenuService;
import com.zmy.yorvix.service.system.RoleService;
import com.zmy.yorvix.service.user.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户接口（**本人域**）。
 * <p>与 {@code /api/system/**} 的区别：这里所有操作都**限定为当前登录用户本人**，
 * 不接受目标用户 ID 参数，因此只需登录态、不需要 ADMIN 角色。
 * <p>对应 vben 前端契约 {@code GET /api/user/info}，以及个人中心的资料读写。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInfoController {

  private final MenuService menuService;
  private final RoleService roleService;
  private final UserProfileService userProfileService;

  /** 获取当前用户信息（需 Bearer token）：真实角色 + 动态 homePath + 无权限原因 */
  @GetMapping("/info")
  public Result<CurrentUserInfo> info() {
    LoginUser user = AuthContext.require();
    // 首页与"是否有菜单"一次算完，避免重复查询菜单表
    MenuService.UserMenuInfo menuInfo = menuService.resolveUserMenuInfo(user);
    CurrentUserInfo info = CurrentUserInfo.of(user, menuInfo.homePath());
    info.setPermissionIssue(resolvePermissionIssue(user, menuInfo.hasMenu()));
    return Result.ok(info);
  }

  /**
   * 读取本人资料（个人中心「基本设置」）。
   * <p>不接受目标用户 ID：操作对象恒为当前登录用户，因此不存在"改别人资料"的越权面。
   */
  @GetMapping("/profile")
  public Result<ProfileItem> profile() {
    return Result.ok(userProfileService.get(AuthContext.require().getUserId()));
  }

  /**
   * 更新本人资料（昵称 / 邮箱 / 手机号）。
   * <p>返回更新后的资料，前端可直接就地刷新，省掉一次 GET 往返。
   * <p>账号治理字段（username / status / is_super / 角色）**不在**本接口范围内，
   * 只能由管理员在 {@code /api/system/users/**} 修改——否则等于把提权入口开放给所有人。
   */
  @PutMapping("/profile")
  public Result<ProfileItem> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
    return Result.ok(userProfileService.update(AuthContext.require().getUserId(), request));
  }

  /**
   * 计算"无权限原因"（null 表示正常）。
   * <p>角色被禁用与压根没分配角色都会导致角色列表为空，但提示语必须区分，
   * 否则用户会按错误的方向排查（去问"为什么没给我分配角色"）。
   */
  private String resolvePermissionIssue(LoginUser user, boolean hasMenu) {
    if (user.getRoles() == null || user.getRoles().isEmpty()) {
      return roleService.hasRoleBinding(user.getUserId()) ? "ROLE_DISABLED" : "NO_ROLE";
    }
    if (!user.hasRole(RoleService.ADMIN_CODE) && !hasMenu) {
      // 判定用"有没有可见菜单"，而不是"有没有首页"：只授权了目录/内嵌/外链时
      // 首页为空但菜单是有的，不该提示"未授权任何菜单"。
      // 管理员/超管的菜单是代码层全量放行、不参与授权，这里不做拦截，避免把管理员挡在外面
      return "NO_MENU";
    }
    return null;
  }
}
