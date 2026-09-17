package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 用户编辑请求（用户名/密码不可经此修改：改密走修改密码与重置密码接口）。
 */
@Getter
@Setter
public class UserUpdateRequest {

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

  /** 分配的角色 ID 列表（可空表示清空） */
  private List<Long> roleIds;
}
