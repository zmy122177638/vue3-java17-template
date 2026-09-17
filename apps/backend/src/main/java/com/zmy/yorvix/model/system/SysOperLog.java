package com.zmy.yorvix.model.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志（审计）：系统管理域的写操作逐条留痕。
 * <p>写入方是 {@code security/OperationLogInterceptor}，记录口径（只记非 GET）也定义在那里。
 * <p>表结构见 {@code db/migration/V1__baseline.sql}；{@code created_at} 由数据库默认值维护。
 */
@Getter
@Setter
@TableName("sys_oper_log")
public class SysOperLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 操作人 ID（未登录或令牌无效时为 null） */
  private Long userId;

  /** 操作人账号（冗余存储，避免列表查询联表） */
  private String username;

  /**
   * 模块，同时是审计要求的「客体标识」中的**对象类型**（由请求路径推断，如 users / roles / menus）。
   * <p>与 {@link #targetId} 合起来表达"改了哪一条数据"（如 {@code users + 3}）。
   * 二者当前同源，因此不再另设 {@code target_type} 列——重复字段只会带来不一致的风险。
   */
  private String module;

  /** 操作对象主键；新建、或 {@code /menus/sort} 这类无 id 的动作为 null */
  private Long targetId;

  /** HTTP 方法（POST / PUT / DELETE） */
  private String method;

  /** 请求路径 */
  private String uri;

  /** 来源 IP */
  private String ip;

  /** HTTP 状态码 */
  private Integer status;

  /** 是否成功 1 是 0 否（业务失败也算失败） */
  private Integer success;

  /** 失败原因的 i18n key，成功为 null */
  private String errorMessage;

  /** 耗时（毫秒） */
  private Long durationMs;

  /** 请求链路标识，与日志中的 traceId 同值（来源见 config/TraceIdFilter） */
  private String traceId;

  /** 发生时间（数据库默认值维护） */
  private LocalDateTime createdAt;
}
