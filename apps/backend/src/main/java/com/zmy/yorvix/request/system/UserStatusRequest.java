package com.zmy.yorvix.request.system;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户状态切换请求（列表开关）。
 * <p>用 DTO 而不是 Map 接参：字段名与取值都有契约，避免 {@code status=null} 直接落库撞 NOT NULL，
 * 也避免写入 0/1 之外的非法状态。
 */
@Getter
@Setter
public class UserStatusRequest {

  @NotNull
  @Min(0)
  @Max(1)
  private Integer status;
}
