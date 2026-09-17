package com.zmy.yorvix.response.system;

import com.zmy.yorvix.common.i18n.Messages;
import com.zmy.yorvix.model.system.SysOperLog;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志列表项。
 * <p>失败原因下发两个字段：{@code errorMessage} 是**按当前请求语言解析后的文案**
 * （列表直接展示），{@code errorMessageKey} 是库里的原始 i18n key
 * （如 {@code error.role.not.found}），用于与后端应用日志、代码对照。
 * <p>库里始终只存 key（语言无关、可搜索），解析放在下发时做——这样同一条记录
 * 在不同语言的请求下得到不同文案，而数据本身保持稳定。
 * <p>成功时两个字段都为 null。
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
  /** 失败原因（按 Accept-Language 解析后的文案）；成功为 null */
  private String errorMessage;
  /** 失败原因的原始 i18n key；成功为 null */
  private String errorMessageKey;
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
    // 库里存的是 i18n key（语言无关）：这里按当前请求语言解析成文案下发，
    // 同时保留裸 key 供与后端日志/代码对照（Messages 依赖 LocaleContextHolder，
    // 而列表查询就在请求线程内，因此能拿到正确的 Accept-Language）
    item.setErrorMessageKey(log.getErrorMessage());
    item.setErrorMessage(Messages.get(log.getErrorMessage()));
    item.setDurationMs(log.getDurationMs());
    item.setTraceId(log.getTraceId());
    item.setCreatedAt(log.getCreatedAt());
    return item;
  }
}
