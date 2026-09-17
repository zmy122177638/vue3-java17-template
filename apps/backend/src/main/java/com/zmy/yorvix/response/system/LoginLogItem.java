package com.zmy.yorvix.response.system;

import com.zmy.yorvix.common.i18n.Messages;
import com.zmy.yorvix.model.system.SysLoginLog;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录日志列表项。
 * <p>{@code event} 是机器可判定的枚举字符串（SUCCESS/FAILURE/DISABLED/LOCKED/LOGOUT），
 * 由前端按 i18n key {@code system.log.event.<EVENT>} 展示——它是**状态枚举**而不是
 * 面向用户的错误文案，因此不走后端的 message 本地化。
 * <p>{@code detail} 与 {@code errorMessage} 不同：它表达的是"这次登录为什么失败"，
 * 库里存 i18n key（语言无关），下发时解析成当前语言的文案，裸 key 一并带在
 * {@code detailKey} 里供与后端日志、代码对照。
 */
@Getter
@Setter
public class LoginLogItem {

  private Long id;
  private String username;
  private Long userId;
  private String event;
  private String ip;
  private String userAgent;
  /** 补充信息（按 Accept-Language 解析后的文案）；无补充信息时为 null */
  private String detail;
  /** 补充信息的原始 i18n key；无补充信息时为 null */
  private String detailKey;
  /** 请求链路标识，可据此在应用日志中检索同一次请求的完整轨迹 */
  private String traceId;
  private LocalDateTime createdAt;

  public static LoginLogItem of(SysLoginLog log) {
    LoginLogItem item = new LoginLogItem();
    item.setId(log.getId());
    item.setUsername(log.getUsername());
    item.setUserId(log.getUserId());
    item.setEvent(log.getEvent());
    item.setIp(log.getIp());
    item.setUserAgent(log.getUserAgent());
    // 与 OperLogItem 同理：库里存 key，下发时解析，裸 key 另存一列供对照
    item.setDetailKey(log.getDetail());
    item.setDetail(Messages.get(log.getDetail()));
    item.setTraceId(log.getTraceId());
    item.setCreatedAt(log.getCreatedAt());
    return item;
  }
}
