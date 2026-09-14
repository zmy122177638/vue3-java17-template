package com.zmy.yorvix.response.auth;

import com.zmy.yorvix.security.LoginUser;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

/**
 * vben 前端契约的当前用户信息（GET /api/user/info）。
 * <p>字段名与前端 {@code UserInfo} 类型对齐；roles 固定为 ADMIN，
 * 供前端访问控制使用，可按项目需要扩展。
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
  /** 登录后跳转页（对应前端本地路由 /dashboard/workspace） */
  private String homePath;
  private ArrayList<String> roles;

  public static CurrentUserInfo of(LoginUser user) {
    CurrentUserInfo info = new CurrentUserInfo();
    info.setUserId(String.valueOf(user.getUserId()));
    info.setUsername(user.getUsername());
    info.setRealName(user.getNickname() == null || user.getNickname().isBlank()
        ? user.getUsername()
        : user.getNickname());
    info.setAvatar(DEFAULT_AVATAR);
    info.setDesc("yorvix 通用开发模板");
    info.setHomePath("/dashboard/workspace");
    info.setRoles(new ArrayList<>(java.util.List.of("ADMIN")));
    return info;
  }
}
