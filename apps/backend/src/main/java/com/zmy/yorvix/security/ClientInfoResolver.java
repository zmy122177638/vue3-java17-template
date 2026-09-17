package com.zmy.yorvix.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 从请求中解析客户端信息，供登录限流与审计日志使用。
 *
 * <h2>为什么不直接读 {@code X-Forwarded-For} 的第一个值</h2>
 * 该请求头是**调用方可控输入**。常见的 Nginx 写法 {@code proxy_add_x_forwarded_for}
 * 语义是"把对端地址追加到末尾"（{@code $http_x_forwarded_for + ", " + $remote_addr}），
 * 因此攻击者可以自己带一个 {@code X-Forwarded-For: 1.2.3.4}，
 * 代理会把它变成 {@code 1.2.3.4, <真实IP>} —— 此时**取第一个值恰好是被伪造的那个**，
 * 按 IP 限流就形同虚设。
 * <p>所以这里的规则是：配置了 {@code yorvix.security.client-ip-header} 时才读该头，
 * 并且取**最后一个**非空项（单层可信代理下它就是真实客户端地址）。
 * <p>该配置默认留空 = 用 {@code remoteAddr}（永远可信）。**只有在应用确实位于
 * 可信反向代理之后才可以配置它**，否则等于把限流键交给请求方随意指定。
 */
public final class ClientInfoResolver {

  /** 无法解析 IP 时的占位值，与 sys_login_log.ip / sys_oper_log.ip 的列宽保持一致 */
  public static final String UNKNOWN = "unknown";

  /** 与 sys_login_log.ip / sys_oper_log.ip（VARCHAR(64)）一致 */
  private static final int MAX_IP_LENGTH = 64;

  /** 与 sys_login_log.user_agent（VARCHAR(255)）一致 */
  private static final int MAX_USER_AGENT_LENGTH = 255;

  private static final String USER_AGENT_HEADER = "User-Agent";

  private ClientInfoResolver() {
  }

  /** 解析来源 IP 与 User-Agent；任何一项取不到都不会抛出异常 */
  public static ClientInfo resolve(HttpServletRequest request, String clientIpHeader) {
    if (request == null) {
      return ClientInfo.unknown();
    }
    return new ClientInfo(resolveIp(request, clientIpHeader),
        truncate(safeHeader(request, USER_AGENT_HEADER), MAX_USER_AGENT_LENGTH));
  }

  /** 解析来源 IP：优先配置的请求头（取最后一项），否则 remoteAddr */
  static String resolveIp(HttpServletRequest request, String clientIpHeader) {
    if (StringUtils.hasText(clientIpHeader)) {
      String forwarded = truncate(safeHeader(request, clientIpHeader.trim()), MAX_IP_LENGTH);
      String last = lastForwardedValue(forwarded);
      if (StringUtils.hasText(last)) {
        return last;
      }
    }
    String remote = truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
    return StringUtils.hasText(remote) ? remote : UNKNOWN;
  }

  /**
   * 取逗号分隔列表的最后一个非空项。
   * <p>见类注释：单层可信代理会把真实客户端地址追加在末尾。
   */
  private static String lastForwardedValue(String headerValue) {
    if (!StringUtils.hasText(headerValue)) {
      return null;
    }
    String[] parts = headerValue.split(",");
    for (int i = parts.length - 1; i >= 0; i--) {
      String part = parts[i].trim();
      if (!part.isEmpty()) {
        return part;
      }
    }
    return null;
  }

  /**
   * {@code getHeader} 对非法头名会抛异常（如含空格的配置笔误），
   * 这里兜底为 null 并退化到 remoteAddr —— 解析 IP 失败不该让登录接口 500。
   */
  private static String safeHeader(HttpServletRequest request, String name) {
    try {
      return request.getHeader(name);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** 截断到列宽：请求头是外部可控输入，直接落库会超长报错或污染日志 */
  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
  }
}
