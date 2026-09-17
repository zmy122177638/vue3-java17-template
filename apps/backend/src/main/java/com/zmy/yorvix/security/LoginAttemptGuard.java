package com.zmy.yorvix.security;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.TokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * 登录失败限流：对**账号**与**来源 IP** 两个维度分别计数，任一维度达到上限即拒绝登录。
 *
 * <h2>为什么登录接口必须有这个</h2>
 * 登录是唯一的匿名写入口，而它的校验成本是刻意做高的（PBKDF2 十万次迭代）。
 * 没有限流时一次请求就能换来十万次哈希计算，攻击者用很小的成本就能同时做到：
 * <ul>
 *   <li>并行爆破口令（无锁定 = 可以无限尝试）；</li>
 *   <li>耗尽 CPU（每次尝试都是重计算）；</li>
 *   <li>占满数据库连接（登录在事务里做密码校验时表现得尤其明显）。</li>
 * </ul>
 *
 * <h2>两个维度分别解决什么</h2>
 * <ul>
 *   <li><b>账号维度</b>：防止针对某个账号的持续爆破；</li>
 *   <li><b>IP 维度</b>：防止用一个 IP 撞库（换账号名继续试），
 *       这是账号维度拦不住的部分。</li>
 * </ul>
 *
 * <h2>计数与过期语义</h2>
 * 首次失败时写入 TTL（固定窗口，不随每次失败顺延），因此正常情况下锁定时间不会
 * 被攻击者的持续尝试无限拉长；登录成功则两个计数立即清零。
 *
 * <h2>可用性取舍</h2>
 * 本类**不做 fail-open**：Redis 不可用时异常会向上抛。这是刻意的——
 * 令牌状态本身就存在 Redis（{@code security/TokenService}），Redis 挂掉时登录
 * 本来也无法完成，此时"放行登录"只会让限流在真正的故障窗口里失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

  /** 与 security/TokenService 的 auth:* 前缀保持同一命名空间 */
  private static final String KEY_PREFIX = "auth:login:fail:";
  private static final String USER_DIMENSION = "user:";
  private static final String IP_DIMENSION = "ip:";

  /** Redis key 中账号部分的长度上限，防御性截断（登录请求已限制 username 长度） */
  private static final int MAX_USERNAME_IN_KEY = 64;

  private static final long SECONDS_PER_MINUTE = 60;

  private final StringRedisTemplate redisTemplate;
  private final TokenProperties tokenProperties;

  /**
   * 是否已被锁定；锁定则直接抛出 {@link ResultCode#LOGIN_LOCKED}。
   * <p><b>必须在查库与校验口令之前调用</b>：放在后面等于没防住 CPU 耗尽。
   */
  public void checkAllowed(String username, String clientIp) {
    int maxAttempts = maxAttempts();
    long lockedSeconds = Math.max(remainingLockSeconds(userKey(username), maxAttempts),
        remainingLockSeconds(ipKey(clientIp), maxAttempts));
    if (lockedSeconds <= 0) {
      return;
    }
    long minutes = Math.max(1, (lockedSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE);
    log.warn("登录已被限流: username={}, ip={}, 剩余 {} 分钟", username, clientIp, minutes);
    throw new BizException(ResultCode.LOGIN_LOCKED, ResultCode.LOGIN_LOCKED.getMessageKey(),
        minutes);
  }

  /** 记录一次凭据校验失败（账号与 IP 两个维度同时 +1） */
  public void recordFailure(String username, String clientIp) {
    increment(userKey(username));
    increment(ipKey(clientIp));
  }

  /** 登录成功：清零两个维度的计数 */
  public void reset(String username, String clientIp) {
    redisTemplate.delete(userKey(username));
    redisTemplate.delete(ipKey(clientIp));
  }

  // ---------------- 内部 ----------------

  private void increment(String key) {
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      // 只在第一次失败时写入 TTL：固定窗口，避免攻击者用持续尝试把锁定时间无限顺延
      redisTemplate.expire(key, Duration.ofMinutes(lockMinutes()));
    }
  }

  /**
   * 该 key 还需锁定多少秒；未达上限或无 TTL 记录时返回 0。
   * <p>用 GET 而不是 INCR：检查路径不能产生副作用（否则每次检查都会计数）。
   */
  private long remainingLockSeconds(String key, int maxAttempts) {
    String value = redisTemplate.opsForValue().get(key);
    if (!StringUtils.hasText(value) || parseLong(value) < maxAttempts) {
      return 0;
    }
    Long ttl = redisTemplate.getExpire(key);
    // ttl 为 -1（无过期时间）时按完整锁定时长处理：宁可多锁，也不要在异常状态下失去限流
    return ttl == null || ttl < 0 ? lockMinutes() * SECONDS_PER_MINUTE : ttl;
  }

  private long parseLong(String value) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      // 计数被外部改坏时按"未达上限"处理，不要因为脏数据把所有人锁在门外
      log.warn("登录失败计数不是数字，已忽略: value={}", value);
      return 0;
    }
  }

  private String userKey(String username) {
    return KEY_PREFIX + USER_DIMENSION + normalizeUsername(username);
  }

  private String ipKey(String clientIp) {
    return KEY_PREFIX + IP_DIMENSION + (StringUtils.hasText(clientIp) ? clientIp : ClientInfoResolver.UNKNOWN);
  }

  /**
   * 账号维度 key 归一化：去空白 → 统一小写 → 按 Redis key 长度上限截断。
   *
   * <p><b>为什么必须小写归一</b>：限流 key 必须与「查库时判定为同一账号」的口径一致。
   * {@code sys_user.username} 的唯一索引在 utf8mb4_unicode_ci 下**大小写不敏感**，
   * 即 Admin / admin / ADMIN 会命中同一个用户；但若 key 保留原始大小写，它们就是三个
   * 独立计数——攻击者只需变换大小写，账号维度的计数便永远停在 1，只剩 IP 维度兜底
   * （换 IP 池后账号维度实际上已失效）。
   *
   * <p>用 {@code Locale.ROOT} 而不是默认 Locale：避免土耳其语环境下 "I" 的小写变成 "ı"，
   * 让限流键随部署机器的区域设置漂移。
   */
  private String normalizeUsername(String username) {
    if (username == null) {
      return "";
    }
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    return normalized.length() <= MAX_USERNAME_IN_KEY
        ? normalized
        : normalized.substring(0, MAX_USERNAME_IN_KEY);
  }

  private int maxAttempts() {
    return (int) Math.max(1, tokenProperties.getSecurity().getLoginMaxAttempts());
  }

  private long lockMinutes() {
    return Math.max(1, tokenProperties.getSecurity().getLoginLockMinutes());
  }
}
