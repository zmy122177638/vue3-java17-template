package com.zmy.yorvix.response.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 本人资料（个人中心「基本设置」）。
 * <p>与 {@code CurrentUserInfo}（vben 的 {@code /api/user/info} 契约）分开：
 * 后者是前端框架要求的固定结构（userId/realName/avatar/homePath/roles），
 * 前者是"可编辑的资料字段"。混在一起会让"改一个资料字段"影响登录链路的关键契约。
 */
@Getter
@Setter
public class ProfileItem {

  /** 登录账号（只读展示，不可修改） */
  private String username;

  private String nickname;
  private String email;
  private String phone;

  /** 角色编码（只读展示，让用户知道自己为什么有这些权限） */
  private List<String> roles = new ArrayList<>();
}
