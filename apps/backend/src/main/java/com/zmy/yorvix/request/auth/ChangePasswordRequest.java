package com.zmy.yorvix.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改密码请求。
 */
@Getter
@Setter
public class ChangePasswordRequest {

  @NotBlank(message = "原密码不能为空")
  private String oldPassword;

  @NotBlank(message = "新密码不能为空")
  @Size(min = 8, max = 32, message = "新密码长度需在 8-32 位之间")
  private String newPassword;
}
