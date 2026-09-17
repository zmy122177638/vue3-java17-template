package com.zmy.yorvix.security;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.TokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流。
 * <p>这是典型的"错了也不报错、只是安全属性悄悄消失"的逻辑（阈值判定、TTL、两个维度的 key），
 * 因此这里逐条钉死：<b>达到阈值必须拦、未达阈值必须放</b>，且账号与 IP 两个维度互不干扰。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTest {

  private static final String USER_KEY = "auth:login:fail:user:alice";
  private static final String IP_KEY = "auth:login:fail:ip:10.0.0.1";

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private LoginAttemptGuard guard;

  @BeforeEach
  void setUp() {
    TokenProperties properties = new TokenProperties();
    properties.getSecurity().setLoginMaxAttempts(5);
    properties.getSecurity().setLoginLockMinutes(15);
    guard = new LoginAttemptGuard(redisTemplate, properties);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("未达阈值放行；没有计数记录同样放行")
  void allowsWhenBelowThreshold() {
    when(valueOperations.get(USER_KEY)).thenReturn("4");

    assertThatCode(() -> guard.checkAllowed("alice", "10.0.0.1"))
        .doesNotThrowAnyException();

    when(valueOperations.get(USER_KEY)).thenReturn(null);
    assertThatCode(() -> guard.checkAllowed("alice", "10.0.0.1"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("达到阈值时拒绝，并把剩余分钟数作为占位符传给前端文案")
  void blocksWhenThresholdReached() {
    when(valueOperations.get(USER_KEY)).thenReturn("5");
    when(redisTemplate.getExpire(USER_KEY)).thenReturn(300L);

    BizException thrown = catchThrowableOfType(() -> guard.checkAllowed("alice", "10.0.0.1"),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCode()).isEqualTo(ResultCode.LOGIN_LOCKED.getCode());
    // 300 秒 -> 5 分钟（向上取整），文案模板是 "请 {0} 分钟后重试"
    assertThat(thrown.getArgs()).isEqualTo(new Object[] {5L});
  }

  @Test
  @DisplayName("剩余时间向上取整：1 秒也必须提示至少 1 分钟（不能包装成 0）")
  void roundsRemainingTimeUpToOneMinute() {
    when(valueOperations.get(USER_KEY)).thenReturn("5");
    when(redisTemplate.getExpire(USER_KEY)).thenReturn(1L);

    BizException thrown = catchThrowableOfType(() -> guard.checkAllowed("alice", "10.0.0.1"),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getArgs()).isEqualTo(new Object[] {1L});
  }

  @Test
  @DisplayName("计数 key 没有 TTL（-1）时仍按完整锁定时长处理：异常状态下也不失去限流")
  void treatsMissingTtlAsFullLock() {
    when(valueOperations.get(USER_KEY)).thenReturn("5");
    when(redisTemplate.getExpire(USER_KEY)).thenReturn(-1L);

    BizException thrown = catchThrowableOfType(() -> guard.checkAllowed("alice", "10.0.0.1"),
        BizException.class);

    assertThat(thrown).isNotNull();
    assertThat(thrown.getArgs()).isEqualTo(new Object[] {15L});
  }

  @Test
  @DisplayName("IP 维度独立生效：换个账号名继续试也会被拦住")
  void blocksByIpDimension() {
    // 账号维度没计数，IP 维度已达阈值 —— 这正是"撞库换账号名"的形态
    when(valueOperations.get(USER_KEY)).thenReturn("0");
    when(valueOperations.get(IP_KEY)).thenReturn("5");
    when(redisTemplate.getExpire(IP_KEY)).thenReturn(600L);

    assertThatThrownBy(() -> guard.checkAllowed("alice", "10.0.0.1"))
        .isInstanceOf(BizException.class);
  }

  @Test
  @DisplayName("计数被外部改坏时按未达阈值处理：脏数据不能把所有人锁在门外")
  void ignoresCorruptedCounter() {
    when(valueOperations.get(USER_KEY)).thenReturn("not-a-number");

    assertThatCode(() -> guard.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("记录失败：账号与 IP 同时 +1，且 TTL 只在首次失败时写入（固定窗口）")
  void recordsFailureOnBothDimensions() {
    when(valueOperations.increment(USER_KEY)).thenReturn(1L);
    when(valueOperations.increment(IP_KEY)).thenReturn(3L);

    guard.recordFailure("alice", "10.0.0.1");

    // 首次失败才写 TTL：否则攻击者持续尝试会把锁定时间无限顺延
    verify(redisTemplate).expire(USER_KEY, Duration.ofMinutes(15));
    verify(redisTemplate, never()).expire(eq(IP_KEY), any(Duration.class));
  }

  @Test
  @DisplayName("登录成功清零两个维度")
  void resetsBothDimensionsOnSuccess() {
    guard.reset("alice", "10.0.0.1");

    verify(redisTemplate).delete(USER_KEY);
    verify(redisTemplate).delete(IP_KEY);
  }

  @Test
  @DisplayName("取不到 IP 时退化为 unknown 维度，不会写入 auth:login:fail:ip: 这种空 key")
  void fallsBackToUnknownIpDimension() {
    when(valueOperations.increment("auth:login:fail:ip:unknown")).thenReturn(1L);
    when(valueOperations.increment(USER_KEY)).thenReturn(1L);

    guard.recordFailure("alice", null);

    verify(redisTemplate).expire("auth:login:fail:ip:unknown", Duration.ofMinutes(15));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  @DisplayName("账号维度归一为小写：变换大小写无法绕过账号维度计数")
  void normalizesUsernameCaseForUserDimension() {
    guard.recordFailure("Alice", "10.0.0.1");

    // 归一后 Alice / alice / ALICE 落在同一个 key 上，与 DB 的 utf8mb4_unicode_ci 口径一致
    verify(valueOperations).increment(USER_KEY);
  }

  @Test
  @DisplayName("检查路径同样归一：用大小写变体也读得到同一份计数，达阈值即被拦")
  void checkAllowedUsesNormalizedUsername() {
    when(valueOperations.get(USER_KEY)).thenReturn("5");
    when(redisTemplate.getExpire(USER_KEY)).thenReturn(300L);

    assertThatThrownBy(() -> guard.checkAllowed("ALICE", "10.0.0.1"))
        .isInstanceOf(BizException.class);
  }
}
