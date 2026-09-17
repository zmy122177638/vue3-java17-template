package com.zmy.yorvix.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 口令散列器。
 * <p>这里的错误都是"不报错但安全属性没了"的类型：比如校验时用了固定盐、
 * 或者比较用了 {@code String.equals}（可被时序侧信道利用）。因此重点锁住：
 * 每次编码的盐必须不同、比较必须能识别被篡改的散列、格式异常必须返回 false 而不是抛异常。
 */
class PasswordEncoderTest {

  /** 与 PasswordEncoder.ITERATIONS 一致；测试里加密/解密都要用它才能对齐耗时与结果 */
  private static final int ITERATIONS = 100_000;

  private final PasswordEncoder encoder = new PasswordEncoder();

  @Test
  @DisplayName("输出格式为 pbkdf2$迭代次数$盐$散列")
  void encodesWithExpectedFormat() {
    String encoded = encoder.encode("S3cret-password");

    String[] parts = encoded.split("\\$");
    assertThat(parts).hasSize(4);
    assertThat(parts[0]).isEqualTo("pbkdf2");
    assertThat(parts[1]).isEqualTo(String.valueOf(ITERATIONS));
    // 盐 16 字节、散列 32 字节，十六进制表示分别是 32 / 64 字符
    assertThat(parts[2]).hasSize(32);
    assertThat(parts[3]).hasSize(64);
  }

  @Test
  @DisplayName("同一口令两次编码结果不同（盐随机），但都能校验通过")
  void usesRandomSaltPerEncoding() {
    String first = encoder.encode("same-password");
    String second = encoder.encode("same-password");

    assertThat(first).isNotEqualTo(second);
    assertThat(encoder.matches("same-password", first)).isTrue();
    assertThat(encoder.matches("same-password", second)).isTrue();
  }

  @Test
  @DisplayName("口令错误、散列被篡改、格式非法都返回 false 且不抛异常")
  void rejectsInvalidInputsWithoutThrowing() {
    String encoded = encoder.encode("correct-password");

    assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    // 篡改散列最后一位：必须校验失败（否则等于任何口令都能通过）
    String tampered = encoded.substring(0, encoded.length() - 1)
        + (encoded.endsWith("0") ? "1" : "0");
    assertThat(encoder.matches("correct-password", tampered)).isFalse();

    // 各种脏数据：格式不对时返回 false，绝不能抛异常把登录接口变成 500
    assertThat(encoder.matches("any", "plain-text")).isFalse();
    assertThat(encoder.matches("any", "bcrypt$100000$aa$bb")).isFalse();
    assertThat(encoder.matches("any", "pbkdf2$100000$not-hex$not-hex")).isFalse();
    assertThat(encoder.matches("any", "pbkdf2$abc$aa$bb")).isFalse();
  }

  @Test
  @DisplayName("null 入参返回 false，不抛 NPE")
  void handlesNullInputs() {
    assertThat(encoder.matches(null, encoder.encode("x"))).isFalse();
    assertThat(encoder.matches("x", null)).isFalse();
  }

  @Test
  @DisplayName("迭代次数从存储值读取：将来提高 ITERATIONS 不会让历史口令失效")
  void verifiesHashesCreatedWithOtherIterationCounts() throws Exception {
    // 手工按更低迭代次数生成一条历史格式的记录（模拟"升级前建的口令"）
    byte[] salt = "0123456789abcdef".getBytes();
    int legacyIterations = 1000;
    PBEKeySpec spec = new PBEKeySpec("legacy-password".toCharArray(), salt, legacyIterations, 256);
    byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        .getEncoded();
    String legacyEncoded = "pbkdf2$" + legacyIterations + "$"
        + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(hash);

    // 这正是"可以平滑提升迭代次数"的前提：校验参数来自存储值而不是当前常量
    assertThat(encoder.matches("legacy-password", legacyEncoded)).isTrue();
    assertThat(encoder.matches("other-password", legacyEncoded)).isFalse();
  }
}
