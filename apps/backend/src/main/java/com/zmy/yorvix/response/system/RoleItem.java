package com.zmy.yorvix.response.system;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色管理列表项。
 */
@Getter
@Setter
public class RoleItem {

  private Long id;
  private String code;
  private String name;
  private Integer status;
  private String remark;
  private LocalDateTime createdAt;
}
