package com.zmy.yorvix.response.system;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理列表项。
 */
@Getter
@Setter
public class UserItem {

  private Long id;
  private String username;
  private String nickname;
  private String email;
  private String phone;
  private Integer status;
  /** 已分配角色 ID（编辑回显） */
  private List<Long> roleIds = new ArrayList<>();
  /** 已分配角色名（列表展示） */
  private List<String> roleNames = new ArrayList<>();
  /**
   * 当前操作者是否可维护该用户（超管账号、管理员账号对普通管理员为 false）。
   * <p>仅用于前端按钮显隐；后端每次操作都会重新校验。
   */
  private Boolean editable = Boolean.TRUE;
  private LocalDateTime createdAt;
}
