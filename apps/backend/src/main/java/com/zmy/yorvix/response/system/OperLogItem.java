package com.zmy.yorvix.response.system;

import com.zmy.yorvix.model.system.SysOperLog;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志列表项。
 * <p>{@code errorMessage} 是 i18n key（如 {@code error.role.bound}），前端按需展示；
 * 成功时为 null。
 */
@Getter
@Setter
public class OperLogItem {

  private Long id;
  private Long userId;
  private String username;
  private String module;
  /** 操作对象主键（客体标识）；新建或无 id 的动作为 null */
  private Long targetId;
  private String method;
  private String uri;
  private String ip;
  private Integer status;
  private Boolean success;
  private String errorMessage;
  private Long durationMs;
  /** 请求链路标识，可据此在应用日志中检索同一次请求的完整轨迹 */
  private String traceId;
  private LocalDateTime createdAt;

  public static OperLogItem of(SysOperLog log) {
    OperLogItem item = new OperLogItem();
    item.setId(log.getId());
    item.setUserId(log.getUserId());
    item.setUsername(log.getUsername());
    item.setModule(log.getModule());
    item.setTargetId(log.getTargetId());
    item.setMethod(log.getMethod());
    item.setUri(log.getUri());
    item.setIp(log.getIp());
    item.setStatus(log.getStatus());
    item.setSuccess(log.getSuccess() != null && log.getSuccess() == 1);
    item.setErrorMessage(log.getErrorMessage());
    item.setDurationMs(log.getDurationMs());
    item.setTraceId(log.getTraceId());
    item.setCreatedAt(log.getCreatedAt());
    return item;
  }
}
