package com.zmy.yorvix.model.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录日志（审计）：成功/失败/被限流/账号禁用/登出逐条留痕。
 * <p>表结构见 {@code db/migration/V1__baseline.sql}；{@code created_at} 由数据库默认值维护，
 * 实体字段留空即可（MyBatis-Plus 默认插入策略会跳过 null 字段）。
 * <p><b>本表只写不删、也不参与任何鉴权判定</b>——它是事后追溯的数据源，
 * 被它影响反而说明设计错了。
 */
@Getter
@Setter
@TableName("sys_login_log")
public class SysLoginLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 尝试登录的账号（失败时也记录，用于识别撞库） */
  private String username;

  /** 账号存在时的用户 ID，否则 null */
  private Long userId;

  /** 事件类型，取值见 {@code AuditLogService.LoginEvent} */
  private String event;

  /** 来源 IP（见 security/ClientInfoResolver） */
  private String ip;

  /** User-Agent（截断后） */
  private String userAgent;

  /** 补充信息（i18n key 或原因），**不得写入任何凭据** */
  private String detail;

  /** 请求链路标识，与日志中的 traceId 同值（来源见 config/TraceIdFilter） */
  private String traceId;

  /** 发生时间（数据库默认值维护） */
  private LocalDateTime createdAt;
}
