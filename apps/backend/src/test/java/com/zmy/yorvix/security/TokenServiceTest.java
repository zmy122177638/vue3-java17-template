package com.zmy.yorvix.security;

import com.zmy.yorvix.config.TokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 令牌服务（Redis 用 mock，不依赖真实 Redis）。
 * <p>重点锁住三件"错了很隐蔽"的事：
 * <ol>
 *   <li>access 与 refresh 必须落在**不同前缀**下——用错前缀会让 refresh token 能当 access token 用，
 *       等于把 7 天有效期的凭证变成可直接访问接口的凭证；</li>
 *   <li>TTL 必须随令牌类型变化（写错就等于让 access token 也活 7 天）；</li>
 *   <li>载荷解析失败必须按"无效"处理，而不是抛异常或返回错误用户。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  private static final String ACCESS_PREFIX = "auth:access:";
  private static final String REFRESH_PREFIX = "auth:refresh:";

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private TokenService tokenService;

  @BeforeEach
  void setUp() {
    TokenProperties properties = new TokenProperties();
    properties.getToken().setExpireMinutes(120);
    properties.getToken().setRefreshExpireMinutes(10080);
    tokenService = new TokenService(redisTemplate, new ObjectMapper(), properties);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("access token：写入 auth:access 前缀，TTL 为 expire-minutes")
  void createsAccessTokenWithAccessPrefixAndTtl() {
    String token = tokenService.createAccessToken(7L, "alice", "Alice", 3);

    ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(eq(ACCESS_PREFIX + token), value.capture(),
        eq(Duration.ofMinutes(120)));
    assertThat(value.getValue())
        .contains("\"userId\":7")
        .contains("\"username\":\"alice\"")
        .contains("\"tokenVersion\":3");
  }

  @Test
  @DisplayName("refresh token：写入 auth:refresh 前缀，TTL 为 refresh-expire-minutes")
  void createsRefreshTokenWithRefreshPrefixAndTtl() {
    String token = tokenService.createRefreshToken(7L, "alice", "Alice", 3);

    verify(valueOperations).set(eq(REFRESH_PREFIX + token), anyString(),
        eq(Duration.ofMinutes(10080)));
    // 关键：access 前缀下不该出现这个令牌（两个前缀必须互不通用）
    verify(valueOperations, never()).set(eq(ACCESS_PREFIX + token), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("token 本体是 256 位随机数的 base64url（43 字符、无填充），且每次不同")
  void generatesOpaqueRandomTokens() {
    String first = tokenService.createAccessToken(1L, "u", "n", 0);
    String second = tokenService.createAccessToken(1L, "u", "n", 0);

    assertThat(first).hasSize(43).doesNotContain("=", "+", "/");
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("载荷可原样读回（含 tokenVersion，用于批量踢下线）")
  void resolvesStoredPayload() {
    when(valueOperations.get(ACCESS_PREFIX + "abc")).thenReturn(
        "{\"userId\":9,\"username\":\"bob\",\"nickname\":\"Bob\",\"tokenVersion\":5}");

    Optional<TokenService.TokenPayload> payload = tokenService.resolveAccess("abc");

    assertThat(payload).isPresent();
    assertThat(payload.get().userId()).isEqualTo(9L);
    assertThat(payload.get().tokenVersion()).isEqualTo(5);
  }

  @Test
  @DisplayName("key 不存在 / token 为空 -> 视为无效（登出、过期都走这条路径）")
  void returnsEmptyForMissingOrBlankToken() {
    assertThat(tokenService.resolveAccess(null)).isEmpty();
    assertThat(tokenService.resolveAccess("  ")).isEmpty();
    assertThat(tokenService.resolveAccess("missing")).isEmpty();
  }

  @Test
  @DisplayName("载荷损坏时按无效处理，而不是抛异常或返回半个用户")
  void returnsEmptyForCorruptedPayload() {
    when(valueOperations.get(ACCESS_PREFIX + "broken")).thenReturn("{not-json");

    assertThat(tokenService.resolveAccess("broken")).isEmpty();
  }

  @Test
  @DisplayName("access 与 refresh 相互不可解析：用错前缀查不到对方")
  void resolvesEachTokenTypeFromItsOwnPrefix() {
    String refreshToken = tokenService.createRefreshToken(1L, "u", "n", 0);

    // 同一串令牌，用 access 前缀去查必然为空（前缀不同 -> key 不同）
    assertThat(tokenService.resolveAccess(refreshToken)).isEmpty();
  }

  @Test
  @DisplayName("吊销即删除对应 key；空 token 不做无谓删除（幂等）")
  void revokesByIdDeletingKey() {
    tokenService.revokeAccess("t1");
    tokenService.revokeRefresh("t2");

    verify(redisTemplate).delete(ACCESS_PREFIX + "t1");
    verify(redisTemplate).delete(REFRESH_PREFIX + "t2");

    tokenService.revokeAccess(null);
    tokenService.revokeRefresh("   ");
    // 空令牌不会产生额外的删除（总数仍是上面那两次）
    verify(redisTemplate, times(2)).delete(anyString());
  }
}
