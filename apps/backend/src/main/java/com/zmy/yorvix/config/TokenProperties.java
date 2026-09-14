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
 *   security: { white-list }
 *   init:     { seed-enabled }
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yorvix")
public class TokenProperties {

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
  }

  @Getter
  @Setter
  public static class Init {
    /** 是否在启动时初始化内置种子账号（生产环境应关闭） */
    private boolean seedEnabled = true;
  }
}
