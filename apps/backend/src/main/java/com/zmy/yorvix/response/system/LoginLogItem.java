package com.zmy.yorvix.response.system;

import com.zmy.yorvix.model.system.SysLoginLog;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录日志列表项。
 * <p>{@code event} 是机器可判定的枚举字符串（SUCCESS/FAILURE/DISABLED/LOCKED/LOGOUT），
 * 由前端按 i18n key {@code system.log.event.<EVENT>} 展示——它是**状态枚举**而不是
 * 面向用户的错误文案，因此不走后端的 message 本地化。
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
  /** 补充信息（i18n key），前端按需展示 */
  private String detail;
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
    item.setDetail(log.getDetail());
    item.setTraceId(log.getTraceId());
    item.setCreatedAt(log.getCreatedAt());
    return item;
  }
}
