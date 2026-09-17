package com.zmy.yorvix.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新本人资料的请求（个人中心「基本设置」）。
 * <p><b>刻意只有这三个字段</b>：username / status / is_super / 角色授权都属于**账号治理**，
 * 只能由管理员在「用户管理」里改。让本人也能改这些，等于把提权入口开放给所有人。
 * <p>字段均为可空：传 null 表示"清空该字段"，因此不能省略——
 * 见 {@code SysUser} 上 nickname/email/phone 的 {@code updateStrategy = ALWAYS}。
 */
@Getter
@Setter
public class ProfileUpdateRequest {

  @Size(max = 64)
  private String nickname;

  @Email
  @Size(max = 128)
  private String email;

  @Size(max = 32)
  private String phone;
}
