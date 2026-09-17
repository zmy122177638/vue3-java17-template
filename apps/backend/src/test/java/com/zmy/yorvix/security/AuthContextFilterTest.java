package com.zmy.yorvix.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuthContext} 的生命周期。
 *
 * <h2>为什么这个测试必须存在</h2>
 * 原实现把清理放在 {@code AuthInterceptor#afterCompletion} 里，而 Spring 在拦截器返回 false 时
 * **不会调用它**，于是"鉴权失败返回 403"这条路径会把 {@code LoginUser} 留在 Tomcat 线程上，
 * 被下一个复用该线程的请求读到。这种缺陷不抛异常、不打日志、功能测试也全绿——
 * 只有把"拒绝后上下文必须为空"写成断言才能守住。
 */
class AuthContextFilterTest {

  private final AuthContextFilter filter = new AuthContextFilter();

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  @Test
  @DisplayName("链路正常结束时清理上下文")
  void clearsContextOnNormalCompletion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean visibleInsideChain = new AtomicBoolean();

    FilterChain chain = (req, res) -> {
      AuthContext.set(loginUser());
      visibleInsideChain.set(AuthContext.getOrNull() != null);
    };

    filter.doFilter(request, response, chain);

    // 链路内可见（业务代码才能用 @CurrentUser）……
    assertThat(visibleInsideChain).isTrue();
    // ……链路外必须为空
    assertThat(AuthContext.getOrNull()).isNull();
  }

  @Test
  @DisplayName("链路抛异常时同样清理（finally 语义）")
  void clearsContextWhenChainThrows() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/info");
    MockHttpServletResponse response = new MockHttpServletResponse();

    FilterChain chain = (req, res) -> {
      AuthContext.set(loginUser());
      throw new IllegalStateException("boom");
    };

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(IllegalStateException.class);
    assertThat(AuthContext.getOrNull()).isNull();
  }

  @Test
  @DisplayName("回归：鉴权失败（preHandle 返回 false，Spring 不回调 afterCompletion）后上下文必须为空")
  void clearsContextWhenRequestIsRejectedByInterceptor() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/system/users/page");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // 复现真实的拒绝路径：拦截器先写入上下文，再返回 false 让请求到此为止。
    // 链路不会继续执行，也就不会有人来"顺手清理"
    FilterChain rejectedChain = (req, res) ->
        AuthContext.set(loginUser());

    filter.doFilter(request, response, rejectedChain);

    assertThat(AuthContext.getOrNull()).isNull();
  }

  @Test
  @DisplayName("请求开始时也清一次：上一请求的残留值不会被本请求读到")
  void clearsStaleContextBeforeChainStarts() throws Exception {
    // 模拟"上一个请求泄漏在复用线程上的值"
    AuthContext.set(loginUser());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean leaked = new AtomicBoolean();

    FilterChain chain = (req, res) -> leaked.set(AuthContext.getOrNull() != null);

    filter.doFilter(request, response, chain);

    assertThat(leaked).isFalse();
  }

  private LoginUser loginUser() {
    return new LoginUser(1L, "alice", "Alice", List.of("ADMIN"), false);
  }
}
