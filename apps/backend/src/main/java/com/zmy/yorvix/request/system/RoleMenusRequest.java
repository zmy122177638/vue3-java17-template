package com.zmy.yorvix.request.system;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 角色菜单授权请求（全量替换）。
 */
@Getter
@Setter
public class RoleMenusRequest {

  /** 授权的菜单/按钮 ID 列表，空表示清空授权 */
  private List<Long> menuIds;
}
