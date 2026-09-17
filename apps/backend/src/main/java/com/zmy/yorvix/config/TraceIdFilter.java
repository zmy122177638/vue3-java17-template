package com.zmy.yorvix.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路标识：把同一次请求产生的所有日志用 {@code traceId} 串起来。
 * <p>解决的问题：并发下日志是交错的，没有标识时无法判断哪几行属于同一次请求。
 * 排查线上问题时，"把这一次请求的完整轨迹捞出来"是最常用的动作。
 * <p>工作方式：
 * <ol>
 *   <li>读请求头 {@code X-Request-Id}（网关/前端已生成时可透传，便于跨系统串联），
 *       没有则生成一个；</li>
 *   <li>写入 MDC（logback pattern 通过 {@code %X{traceId}} 输出，见 application.yaml）；</li>
 *   <li>响应头回传同一个值，便于前端在报错时把它展示/上报，直接对上后端日志；</li>
 *   <li>请求结束（含异常）在 {@code finally} 中清理 MDC —— 线程池会复用线程，
 *       不清理会把上一个请求的 traceId 泄漏给下一个请求。</li>
 * </ol>
 * <p>用 Filter 而不是拦截器：拦截器只覆盖 Controller 请求，缺令牌、参数绑定失败、
 * 静态资源等更早/更晚的失败路径也要有标识。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** MDC key，需与 application.yaml 的 logging.pattern 中 {@code %X{traceId}} 保持一致 */
  public static final String TRACE_ID_KEY = "traceId";

  /** 请求/响应头名称（沿用社区通用名，便于接入网关或 ELK） */
  public static final String TRACE_ID_HEADER = "X-Request-Id";

  /** 透传值的长度上限，避免超长请求头污染日志与响应头 */
  private static final int MAX_LENGTH = 64;

  private static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".TRACE_ID";

  /**
   * 出错转发（如 /error）时也参与，并复用首次生成的 traceId，
   * 否则错误页的那几条日志会与原始请求对不上。
   */
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String traceId = resolveTraceId(request);
    MDC.put(TRACE_ID_KEY, traceId);
    response.setHeader(TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_ID_KEY);
    }
  }

  private String resolveTraceId(HttpServletRequest request) {
    // 二次分发（ERROR 转发）复用首次的值，保证同一次请求只有一个 traceId
    Object existing = request.getAttribute(TRACE_ID_ATTRIBUTE);
    if (existing instanceof String cached && StringUtils.hasText(cached)) {
      return cached;
    }
    String traceId = sanitize(request.getHeader(TRACE_ID_HEADER));
    if (traceId == null) {
      traceId = UUID.randomUUID().toString().replace("-", "");
    }
    request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
    return traceId;
  }

  /**
   * 只接受安全的透传值：限长 + 仅字母/数字/连字符/下划线。
   * <p>请求头是外部可控输入，直接写进日志会被用于注入换行、伪造日志行。
   */
  private String sanitize(String incoming) {
    if (!StringUtils.hasText(incoming) || incoming.length() > MAX_LENGTH) {
      return null;
    }
    boolean safe = incoming.chars().allMatch(c -> Character.isLetterOrDigit(c)
        || c == '-' || c == '_');
    return safe ? incoming : null;
  }
}
