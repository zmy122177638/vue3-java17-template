package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 用户新增请求。
 */
@Getter
@Setter
public class UserCreateRequest {

  @NotBlank
  @Size(max = 64)
  private String username;

  @NotBlank
  @Size(min = 6, max = 32)
  private String password;

  @Size(max = 64)
  private String nickname;

  @Email
  @Size(max = 128)
  private String email;

  @Size(max = 32)
  private String phone;

  @Min(0)
  @Max(1)
  private Integer status;

  /** 分配的角色 ID 列表（可空） */
  private List<Long> roleIds;
}
