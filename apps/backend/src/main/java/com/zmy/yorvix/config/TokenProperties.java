package com.zmy.yorvix.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证令牌与安全相关配置项，对应 YAML 结构：
 * <pre>
 * yorvix:
 *   token:    { expire-minutes, refresh-expire-minutes }
 *   security: { white-list, cors-allowed-origins, cookie-secure }
 *   init:     { seed-enabled, seed-password, verify-schema }
 * </pre>
 * <p>默认值面向<b>本地开发</b>（开箱即用）。生产请激活 prod profile
 * （{@code SPRING_PROFILES_ACTIVE=prod}），它会关闭种子数据、关闭 schema 自动执行、
 * 收敛日志级别并要求显式配置跨域来源——详见 {@code application-prod.yaml}。
 * 若忘记激活，{@code StartupChecks} 会在启动日志中打出醒目的告警。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yorvix")
public class TokenProperties {

  /** 种子超管账号的默认密码（与 {@link Init#getSeedPassword()} 初始值一致，供启动自检比对） */
  public static final String DEFAULT_SEED_PASSWORD = "123456";

  /** 令牌配置（yorvix.token.*） */
  private Token token = new Token();

  /** 免鉴权路径前缀列表（yorvix.security.*） */
  private Security security = new Security();

  /** 启动初始化（yorvix.init.*） */
  private Init init = new Init();

  // ---- 便捷委托：保持既有 getExpireMinutes()/getRefreshExpireMinutes() 调用点不变 ----

  public long getExpireMinutes() {
    return token.getExpireMinutes();
  }

  public long getRefreshExpireMinutes() {
    return token.getRefreshExpireMinutes();
  }

  @Getter
  @Setter
  public static class Token {
    /** access token 有效期（分钟），同时是 Redis key 的 TTL */
    private long expireMinutes = 120;

    /** refresh token 有效期（分钟），默认 7 天 */
    private long refreshExpireMinutes = 10080;
  }

  @Getter
  @Setter
  public static class Security {
    private List<String> whiteList = new ArrayList<>();

    /**
     * 允许跨域的来源（{@code yorvix.security.cors-allowed-origins}，逗号分隔）。
     * <p><b>留空 = 不注册 CORS</b>，即只允许同源请求——这是生产推荐的形态
     * （前端静态资源与后端由 Nginx 同源反代，本就不需要跨域）。
     * <p>开发默认 {@code *} 仅为配合前端 5666 端口联调；生产必须显式配置受信域名，
     * 不要使用 {@code *}，否则任何站点都能发起携带凭证的跨域请求。
     */
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("*"));

    /**
     * refresh token Cookie 是否带 {@code Secure} 标志（HTTPS 环境必须为 true）。
     * <p>开发环境走 http 故默认 false；prod profile 置为 true。
     */
    private boolean cookieSecure = false;

    /**
     * 严格模式（{@code yorvix.security.strict-mode}）：启动时检测到确定性危险配置
     * （种子账号开启 / CORS 含 {@code *} / Cookie 未启用 Secure）就**拒绝启动**，
     * 而不是只打一行容易被容器编排淹没的告警。
     * <p>默认 {@code false}：本地开发依赖这些宽松默认值开箱即用，把默认值直接改成生产值
     * 会破坏这个前提。prod profile 在 {@code application-prod.yaml} 中置为 true，
     * 因此「变严」只发生在生产，开发环境行为完全不变。
     */
    private boolean strictMode = false;

    /**
     * 同一账号/IP 允许的连续登录失败次数，达到后临时拒绝登录（{@code LOGIN_MAX_ATTEMPTS}）。
     * <p>登录是唯一的匿名写入口，且口令校验刻意昂贵（PBKDF2 十万次迭代），
     * 不限流等于把 CPU 与数据库连接交给攻击者。判定逻辑见
     * {@code security/LoginAttemptGuard}。
     */
    private long loginMaxAttempts = 5;

    /** 登录被锁定后的时长（分钟），{@code LOGIN_LOCK_MINUTES} */
    private long loginLockMinutes = 15;

    /**
     * 取真实客户端 IP 的请求头名（{@code CLIENT_IP_HEADER}），留空 = 用 {@code remoteAddr}。
     * <p><b>只有在应用确实位于可信反向代理之后才可以配置</b>（如 Nginx 的 {@code X-Real-IP}）：
     * 该头是调用方可控输入，直连暴露时配置它等于让攻击者自选限流键。
     * 细节见 {@code security/ClientInfoResolver}。
     */
    private String clientIpHeader = "";
  }

  @Getter
  @Setter
  public static class Init {
    /** 是否在启动时初始化内置种子账号（生产环境应关闭） */
    private boolean seedEnabled = true;

    /**
     * 种子超管账号的初始密码（{@code yorvix.init.seed-password}）。
     * <p>默认值仅供本地开发；生产应通过 {@code SEED_ADMIN_PASSWORD} 注入强口令，
     * 或直接关闭 {@code seed-enabled} 并手工建号。
     */
    private String seedPassword = DEFAULT_SEED_PASSWORD;

    /**
     * 启动时是否校验数据库结构是否与实体一致（{@code yorvix.init.verify-schema}）。
     * <p>缺失列会导致运行期 500，这里把它提前成启动期明确报错。默认开启。
     */
    private boolean verifySchema = true;
  }
}
