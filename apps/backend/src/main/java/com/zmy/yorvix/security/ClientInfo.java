package com.zmy.yorvix.security;

/**
 * 客户端信息（来源 IP + User-Agent），认证与审计链路共用。
 * <p>为什么要把这两项从 {@code HttpServletRequest} 里"提前取出来"：
 * <ol>
 *   <li>登录限流（{@link LoginAttemptGuard}）与审计日志都需要它，
 *       而 service 层不应该依赖 Servlet API；</li>
 *   <li>取用点（拦截器/控制器）与消费点（service/拦截器的 {@code afterCompletion}）
 *       不在同一时刻，请求对象在异步/转发场景下可能已被回收。</li>
 * </ol>
 * <p>两个字段都已在 {@link ClientInfoResolver} 中截断到与数据库列一致的长度，
 * 可以直接落库。
 *
 * @param ip        来源 IP，取不到时为 {@link ClientInfoResolver#UNKNOWN}（不会是 null）
 * @param userAgent User-Agent 原文（截断后），取不到时为 null
 */
public record ClientInfo(String ip, String userAgent) {

  /** 无请求上下文时使用的占位值（如后台线程触发的审计） */
  public static ClientInfo unknown() {
    return new ClientInfo(ClientInfoResolver.UNKNOWN, null);
  }
}
