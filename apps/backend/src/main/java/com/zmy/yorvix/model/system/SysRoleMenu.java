package com.zmy.yorvix.model.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色菜单授权（ADMIN 内置管理员不查此表）。
 */
@Getter
@Setter
@TableName("sys_role_menu")
public class SysRoleMenu {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long roleId;

  private Long menuId;
}
