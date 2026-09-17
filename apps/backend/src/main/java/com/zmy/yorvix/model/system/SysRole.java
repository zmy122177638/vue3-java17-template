package com.zmy.yorvix.model.system;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色。code=ADMIN 为内置管理员（代码层全量放行，不可删除/改码）。
 * <p><b>可清空字段必须标 {@code updateStrategy = ALWAYS}</b>：MyBatis-Plus 默认策略是
 * {@code NOT_NULL}，会把值为 null 的字段从 UPDATE 语句里**剔除**，于是"清空备注"
 * 变成"不修改"——接口返回成功、库里还是旧值，属于典型的静默失败。
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 角色编码（唯一），ADMIN 为内置管理员 */
  private String code;

  /** 角色名称 */
  private String name;

  /** 1 启用，0 禁用 */
  private Integer status = 1;

  /** 备注（可清空，理由见类注释） */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String remark;

  /** 创建时间（数据库默认值维护） */
  private LocalDateTime createdAt;

  /** 更新时间（数据库 ON UPDATE 维护） */
  private LocalDateTime updatedAt;

  public boolean isEnabled() {
    return status != null && status == 1;
  }
}
