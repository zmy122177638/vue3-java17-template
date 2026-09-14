package com.zmy.yorvix.security;

import com.zmy.yorvix.config.TokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Opaque Token 服务（不透明令牌 + Redis 存储，无 JWT、无签名密钥）：
 * <ul>
 *   <li>token 本体是 256 位安全随机数的 base64url 编码（43 字符），不携带任何业务信息；</li>
 *   <li>令牌状态集中存放在 Redis：{@code auth:access:{token}} / {@code auth:refresh:{token}}，
 *       value 为 TokenPayload 的 JSON，TTL 即令牌有效期，过期自动清除；</li>
 *   <li>登出 / 刷新即删除对应 key，天然替代旧方案的黑名单表，且跨实例立即生效；</li>
 *   <li>服务重启不会使已签发的令牌失效（状态在 Redis 而非内存）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenService {

  private static final String ACCESS_KEY_PREFIX = "auth:access:";
  private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final TokenProperties properties;

  /** Redis 中存储的令牌载荷 */
  public record TokenPayload(Long userId, String username, String nickname) {
  }

  /** 创建 access token（有效期 yorvix.token.expire-minutes） */
  public String createAccessToken(Long userId, String username, String nickname) {
    return create(ACCESS_KEY_PREFIX, userId, username, nickname,
        Duration.ofMinutes(properties.getExpireMinutes()));
  }

  /** 创建 refresh token（有效期 yorvix.token.refresh-expire-minutes） */
  public String createRefreshToken(Long userId, String username, String nickname) {
    return create(REFRESH_KEY_PREFIX, userId, username, nickname,
        Duration.ofMinutes(properties.getRefreshExpireMinutes()));
  }

  /**
   * 校验 access token 并返回载荷。
   *
   * @throws BizException 401 令牌无效或已过期（Redis 中不存在）
   */
  public Optional<TokenPayload> resolveAccess(String token) {
    return resolve(ACCESS_KEY_PREFIX, token);
  }

  /**
   * 校验 refresh token 并返回载荷。
   *
   * @throws BizException 401 令牌无效或已过期
   */
  public Optional<TokenPayload> resolveRefresh(String token) {
    return resolve(REFRESH_KEY_PREFIX, token);
  }

  /** 删除 access token（登出/刷新后立即失效，幂等） */
  public void revokeAccess(String token) {
    if (token != null && !token.isBlank()) {
      redisTemplate.delete(ACCESS_KEY_PREFIX + token);
    }
  }

  /** 删除 refresh token（登出/轮换后立即失效，幂等） */
  public void revokeRefresh(String token) {
    if (token != null && !token.isBlank()) {
      redisTemplate.delete(REFRESH_KEY_PREFIX + token);
    }
  }

  private String create(String keyPrefix, Long userId, String username, String nickname,
      Duration ttl) {
    String token = randomToken();
    try {
      String value = objectMapper.writeValueAsString(new TokenPayload(userId, username, nickname));
      redisTemplate.opsForValue().set(keyPrefix + token, value, ttl);
      return token;
    } catch (Exception e) {
      throw new IllegalStateException("令牌写入 Redis 失败", e);
    }
  }

  private Optional<TokenPayload> resolve(String keyPrefix, String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    String value = redisTemplate.opsForValue().get(keyPrefix + token);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(value, TokenPayload.class));
    } catch (Exception e) {
      log.warn("令牌载荷解析失败，已按无效处理: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /** 256 位安全随机数 -> base64url（43 字符，无填充） */
  private String randomToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
