package com.zmy.yorvix.model.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统用户（通用登录模板，仅账号信息，不含角色/权限）。
 * <p>MyBatis-Plus 实体：表结构由 schema.sql 统一管理；
 * created_at/updated_at 交由数据库默认值与 ON UPDATE 维护（实体字段留空即可）。
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser {

  /** 主键，数据库自增 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 登录账号（唯一） */
  private String username;

  /** PBKDF2 密码散列（security/PasswordEncoder） */
  private String passwordHash;

  /** 昵称 */
  private String nickname;

  /** 邮箱 */
  private String email;

  /** 手机号 */
  private String phone;

  /** 1 启用，0 禁用 */
  private Integer status = 1;

  /** 创建时间（数据库默认值维护） */
  private LocalDateTime createdAt;

  /** 更新时间（数据库 ON UPDATE 维护） */
  private LocalDateTime updatedAt;

  public boolean isEnabled() {
    return status != null && status == 1;
  }
}
