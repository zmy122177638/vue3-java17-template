package com.zmy.yorvix.config;

import com.zmy.yorvix.security.AuthInterceptor;
import com.zmy.yorvix.security.CurrentUserArgumentResolver;
import com.zmy.yorvix.security.OperationLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置：拦截器、参数解析器、跨域。
 */
@Configuration
@RequiredArgsConstructor
class WebMvcConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final OperationLogInterceptor operationLogInterceptor;
  private final CurrentUserArgumentResolver currentUserArgumentResolver;
  private final TokenProperties tokenProperties;

  /**
   * 拦截器顺序是**语义的一部分**，不要随意调整：
   * <ol>
   *   <li>操作日志在前（order 0）。afterCompletion 按注册逆序回调，且某个拦截器
   *       {@code preHandle} 返回 false 时只会回调"之前已返回 true 的"拦截器——
   *       鉴权失败（403）恰恰是最需要留痕的场景，所以审计必须排在鉴权之前；</li>
   *   <li>鉴权在后（order 1）。它负责写入 {@code AuthContext}，
   *       审计要从那里取操作人。</li>
   * </ol>
   * {@code AuthContext} 的清理不在这两者里，而是由 {@code security/AuthContextFilter}
   * 在过滤器链的 finally 中完成（理由见该类的注释）。
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(operationLogInterceptor).addPathPatterns("/api/**").order(0);
    registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").order(1);
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentUserArgumentResolver);
  }

  /**
   * 跨域来源由 {@code yorvix.security.cors-allowed-origins} 决定。
   * <p><b>留空即不注册 CORS</b>，浏览器只允许同源请求——这是生产推荐形态
   * （前端静态资源与后端由 Nginx 同源反代）。
   * <p>反之若放行 {@code *} 且 {@code allowCredentials(true)}，任何站点都能发起
   * 携带凭证的跨域请求。因此 prod profile 把默认值置空，让"忘记配置"退化为
   * "不允许跨域"（安全），而不是"全开放"（危险）。
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> allowedOrigins = tokenProperties.getSecurity().getCorsAllowedOrigins();
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      return;
    }
    registry.addMapping("/**")
        .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        // 暴露 traceId：跨域场景下浏览器默认只允许脚本读取少数安全响应头，
        // 不显式暴露的话，config/TraceIdFilter 回传的 X-Request-Id 前端读不到，
        // "把 traceId 贴出来对日志"这条排查路径在跨域部署下就断了。
        // （生产推荐同源反代，此时本项无影响）
        .exposedHeaders(TraceIdFilter.TRACE_ID_HEADER)
        .allowCredentials(true)
        .maxAge(3600);
  }
}
