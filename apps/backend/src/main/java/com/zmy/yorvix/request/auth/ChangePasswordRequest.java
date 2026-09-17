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

  @NotBlank
  private String oldPassword;

  @NotBlank
  @Size(min = 6, max = 32)
  private String newPassword;
}
