package com.zmy.yorvix.model.sys;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统用户（通用登录模板，仅账号信息，不含角色/权限）。
 * <p>MyBatis-Plus 实体：表结构由 db/migration 下的 Flyway 迁移统一管理；
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

  /**
   * 昵称。
   * <p>{@code updateStrategy = ALWAYS} 是有意为之：MyBatis-Plus 默认策略是
   * {@code NOT_NULL}，会把值为 null 的字段从 UPDATE 语句里**剔除**，
   * 导致"清空昵称/邮箱/手机号"静默失效（接口返回成功，但值还在库里）。
   * 这三个人可编辑的资料字段必须支持清空，因此显式改为 ALWAYS。
   * <p>不影响其他更新路径：{@code updateStatus} / {@code resetPassword} / 种子校准
   * 都是先从库里读出完整实体再改，这些字段携带的是库中原值。
   */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String nickname;

  /** 邮箱（可清空，理由见 {@link #nickname}） */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String email;

  /** 手机号（可清空，理由见 {@link #nickname}） */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String phone;

  /** 1 启用，0 禁用 */
  private Integer status = 1;

  /** 1 超管，0 普通（仅种子/改库维护，接口层不允许修改） */
  private Integer isSuper = 0;

  /**
   * 令牌版本：递增即让该用户**已签发的全部令牌**立即失效，不依赖 TTL 过期。
   * <p>改密 / 管理员重置密码 / 禁用账号时 +1，用于把该用户所有会话踢下线。
   * <p>{@code updateStrategy = NEVER} 是有意为之：该字段只能通过
   * {@code SysUserMapper#bumpTokenVersion(Long)} 原子递增，禁止 {@code updateById}
   * 把内存中的旧值写回去（否则会出现"本该失效的会话复活"）。
   */
  @TableField(updateStrategy = FieldStrategy.NEVER)
  private Integer tokenVersion = 0;

  /** 创建时间（数据库默认值维护） */
  private LocalDateTime createdAt;

  /** 更新时间（数据库 ON UPDATE 维护） */
  private LocalDateTime updatedAt;

  public boolean isEnabled() {
    return status != null && status == 1;
  }

  /** 是否超级管理员（代码层全量放行，不依赖角色绑定） */
  public boolean isSuperUser() {
    return isSuper != null && isSuper == 1;
  }

  /** 令牌版本（null 视为 0，兼容加列前的历史数据） */
  public int tokenVersionOrDefault() {
    return tokenVersion == null ? 0 : tokenVersion;
  }

  /** 令牌携带的版本是否仍然有效（改密/重置密码/禁用后版本递增，旧令牌全部失效） */
  public boolean matchesTokenVersion(int version) {
    return tokenVersionOrDefault() == version;
  }
}
