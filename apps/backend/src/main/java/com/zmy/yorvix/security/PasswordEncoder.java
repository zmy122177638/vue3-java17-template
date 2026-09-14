package com.zmy.yorvix.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 基于 PBKDF2-HMAC-SHA256 的密码散列器（JDK 内置，无第三方依赖）。
 * <p>存储格式: {@code pbkdf2$<iterations>$<saltHex>$<hashHex>}
 * <p>后续如需对接 Spring Security，可替换为 BCryptPasswordEncoder 实现，
 * 只需保证对外 matches/encode 契约一致。
 */
@Component
public class PasswordEncoder {

  private static final int ITERATIONS = 100_000;
  private static final int KEY_LENGTH = 256; // bits
  private static final int SALT_BYTES = 16;
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

  private final SecureRandom random = new SecureRandom();

  public String encode(String rawPassword) {
    byte[] salt = new byte[SALT_BYTES];
    random.nextBytes(salt);
    byte[] hash = derive(rawPassword, salt, ITERATIONS);
    return "pbkdf2$" + ITERATIONS + "$" + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(hash);
  }

  public boolean matches(String rawPassword, String encoded) {
    if (rawPassword == null || encoded == null) {
      return false;
    }
    String[] parts = encoded.split("\\$");
    if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
      return false;
    }
    try {
      int iterations = Integer.parseInt(parts[1]);
      byte[] salt = HexFormat.of().parseHex(parts[2]);
      byte[] expected = HexFormat.of().parseHex(parts[3]);
      byte[] actual = derive(rawPassword, salt, iterations);
      return MessageDigest.isEqual(expected, actual);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private byte[] derive(String password, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
    } catch (Exception e) {
      throw new IllegalStateException("密码散列计算失败", e);
    }
  }
}
