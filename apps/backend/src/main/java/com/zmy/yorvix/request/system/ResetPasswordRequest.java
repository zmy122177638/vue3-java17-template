package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员重置用户密码请求。
 */
@Getter
@Setter
public class ResetPasswordRequest {

  @NotBlank
  @Size(min = 6, max = 32)
  private String password;
}
