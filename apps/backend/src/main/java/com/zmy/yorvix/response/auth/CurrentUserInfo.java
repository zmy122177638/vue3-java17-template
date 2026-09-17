package com.zmy.yorvix.response.auth;

import com.zmy.yorvix.security.LoginUser;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * vben 前端契约的当前用户信息（GET /api/user/info）。
 * <p>roles 为用户真实角色编码（RBAC），homePath 动态取菜单树首个可见叶子
 * （由 UserInfoController 计算传入）。
 */
@Getter
@Setter
public class CurrentUserInfo {

  /** 与 vben 默认头像保持一致，避免前端头像为空 */
  private static final String DEFAULT_AVATAR =
      "https://unpkg.com/@vbenjs/static-source@0.1.7/source/avatar-v1.webp";

  private String userId;
  private String username;
  /** vben 必填字段，取自 nickname */
  private String realName;
  private String avatar;
  private String desc;
  /** 登录后跳转页（动态下发） */
  private String homePath;
  private ArrayList<String> roles;

  /**
   * 无权限原因（前端据此显示对应的“暂无权限”提示；一切正常时为 null）：
   * <ul>
   *   <li>NO_ROLE —— 未分配任何角色</li>
   *   <li>ROLE_DISABLED —— 已分配角色，但角色全部被禁用/失效</li>
   *   <li>NO_MENU —— 角色生效，但未授权任何菜单</li>
   * </ul>
   */
  private String permissionIssue;

  public static CurrentUserInfo of(LoginUser user, String homePath) {
    CurrentUserInfo info = new CurrentUserInfo();
    info.setUserId(String.valueOf(user.getUserId()));
    info.setUsername(user.getUsername());
    info.setRealName(user.getNickname() == null || user.getNickname().isBlank()
        ? user.getUsername()
        : user.getNickname());
    info.setAvatar(DEFAULT_AVATAR);
    info.setDesc("yorvix 通用开发模板");
    info.setHomePath(homePath);
    info.setRoles(new ArrayList<>(user.getRoles() == null ? List.of() : user.getRoles()));
    return info;
  }
}
