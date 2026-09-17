package com.zmy.yorvix.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动自检：认证链路白名单必需项。
 *
 * <p><b>为什么值得单独测</b>：白名单是"免鉴权清单"，少写一项**不会报任何错**，
 * 只会表现为"这个接口永远 401"。其中 {@code login/refresh/logout} 三项缺失时系统直接不可用——
 * 而且登不进来就没法通过界面自救，只能改配置重启。所以这条保证必须锁死在启动期。
 *
 * <p>测试不起 Spring 上下文：{@code StartupChecks} 的依赖只有 {@code DataSource} 与
 * {@code TokenProperties}，把 {@code verifySchema} 关掉后可传 {@code null} DataSource，
 * 既不连库也不连 Redis。同时把其它告警项置为"安全值"，让日志保持安静。
 */
class StartupChecksTest {

  @Test
  @DisplayName("缺少 login/refresh/logout 任一必需项时拒绝启动，并逐项列出缺失")
  void rejectsMissingRequiredEntries() {
    StartupChecks checks = new StartupChecks(null, safeProperties(List.of("/error")));

    assertThatThrownBy(checks::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("安全配置自检未通过")
        .hasMessageContaining("/api/auth/login")
        .hasMessageContaining("/api/auth/refresh")
        .hasMessageContaining("/api/auth/logout");
  }

  @Test
  @DisplayName("白名单为空同样拒绝启动（否则连登录接口都要鉴权，系统直接锁死）")
  void rejectsEmptyWhitelist() {
    StartupChecks checks = new StartupChecks(null, safeProperties(List.of()));

    assertThatThrownBy(checks::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/api/auth/login");
  }

  @Test
  @DisplayName("只缺其中一项也会被拦住，不会因为其它项存在而放过")
  void rejectsPartiallyCompleteWhitelist() {
    // 少了 logout：登出接口会 401，Redis 中的令牌无法主动清除
    StartupChecks checks = new StartupChecks(null,
        safeProperties(List.of("/api/auth/login", "/api/auth/refresh")));

    assertThatThrownBy(checks::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/api/auth/logout");
  }

  @Test
  @DisplayName("必需项齐备时通过")
  void acceptsCompleteWhitelist() {
    StartupChecks checks = new StartupChecks(null, safeProperties(List.of(
        "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/error")));

    assertThatCode(checks::run).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("用 /** 子树通配覆盖认证链路也算齐备")
  void acceptsWildcardCoverage() {
    StartupChecks checks = new StartupChecks(null,
        safeProperties(List.of("/api/auth/**", "/error")));

    assertThatCode(checks::run).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("严格模式（prod）下不安全默认值拒绝启动，而不是只打一行容易被忽略的告警")
  void strictModeRejectsUnsafeDefaults() {
    TokenProperties properties = new TokenProperties();
    properties.getInit().setVerifySchema(false);
    properties.getInit().setSeedEnabled(true); // 危险：连上全新库会创建 admin 账号
    properties.getSecurity().setStrictMode(true);
    properties.getSecurity().setWhiteList(List.of(
        "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"));
    properties.getSecurity().setCookieSecure(false); // 危险：Cookie 无 Secure 标志
    properties.getSecurity().setCorsAllowedOrigins(List.of("*")); // 危险：任意来源

    StartupChecks checks = new StartupChecks(null, properties);

    assertThatThrownBy(checks::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("种子数据初始化已开启")
        .hasMessageContaining("CORS")
        .hasMessageContaining("Secure");
  }

  @Test
  @DisplayName("严格模式下的生产安全值可正常启动（种子关闭 / CORS 留空 / Cookie Secure）")
  void strictModeAcceptsSafeDefaults() {
    TokenProperties properties = new TokenProperties();
    properties.getInit().setVerifySchema(false);
    properties.getInit().setSeedEnabled(false);
    properties.getSecurity().setStrictMode(true);
    properties.getSecurity().setWhiteList(List.of(
        "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"));
    properties.getSecurity().setCookieSecure(true);
    properties.getSecurity().setCorsAllowedOrigins(List.of());

    StartupChecks checks = new StartupChecks(null, properties);

    assertThatCode(checks::run).doesNotThrowAnyException();
  }

  /**
   * 构造一份"只保留白名单差异"的配置：
   * 关掉表结构校验（不连库），并把其它告警项都设成安全值，避免测试输出噪音。
   */
  private TokenProperties safeProperties(List<String> whiteList) {
    TokenProperties properties = new TokenProperties();
    properties.getInit().setVerifySchema(false);
    properties.getInit().setSeedEnabled(false);
    properties.getInit().setSeedPassword("not-the-default-seed-password");
    properties.getSecurity().setWhiteList(whiteList);
    properties.getSecurity().setCookieSecure(true);
    properties.getSecurity().setCorsAllowedOrigins(List.of("https://example.com"));
    return properties;
  }
}
