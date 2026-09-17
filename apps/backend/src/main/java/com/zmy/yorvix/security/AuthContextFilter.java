package com.zmy.yorvix.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@link AuthContext} 的生命周期管理者：请求进入时清空、请求结束（含异常）时清空。
 *
 * <h2>为什么必须由 Filter 承担，而不能放在拦截器的 afterCompletion</h2>
 * 原来的实现是在 {@code AuthInterceptor#afterCompletion} 里清理 ThreadLocal，
 * 这有一个**静默失效**的漏洞：Spring 的 {@code HandlerExecutionChain.applyPreHandle}
 * 在某个拦截器返回 false 时，只会对"已经返回过 true 的拦截器"调用 afterCompletion
 * （内部游标 {@code interceptorIndex} 还停在 -1）。本项目只注册了一个拦截器，
 * 于是"鉴权失败返回 403"这条路径上 {@code afterCompletion} **永远不会被调用**：
 * <ul>
 *   <li>{@code AuthContext} 里的 {@code LoginUser} 一直留在 Tomcat 工作线程上（线程池复用）；</li>
 *   <li>下一次被该线程接手的请求若恰好是免鉴权路径（login / refresh / logout / error），
 *       拦截器会在设置上下文之前就直接放行，读到的是**上一个用户**的身份。</li>
 * </ul>
 * 当前白名单接口都不读 {@code AuthContext}，所以还不可直接利用；但任何一次
 * "给白名单接口加一句 {@code AuthContext.require()}" 都会立刻变成越权——因此这里
 * 从"靠回调被调用"改成"靠 finally 一定执行"，从机制上消除该类问题，
 * 而不是依赖调用方记得清理。
 *
 * <p>Filter 覆盖的范围比拦截器更广（拦截器只管 {@code /api/**} 的控制器方法），
 * 而清理一个未被设置的 ThreadLocal 是无副作用的，因此这里不做路径限定。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    // 进入前先清一次：即使上一次执行因为非预期原因（如线程被中断）留下了残留值，
    // 也不会被本请求读到
    AuthContext.clear();
    try {
      filterChain.doFilter(request, response);
    } finally {
      // 无论正常返回、抛异常、还是被拦截器提前拒绝，都会走到这里
      AuthContext.clear();
    }
  }
}
