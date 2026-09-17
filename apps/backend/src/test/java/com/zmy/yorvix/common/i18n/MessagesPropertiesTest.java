package com.zmy.yorvix.common.i18n;

import com.zmy.yorvix.common.api.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * i18n 资源文件的一致性检查。
 * <p>这些错误都**不会抛异常**，只会让用户看到一串 key（如 {@code error.username.exists}），
 * 因此值得用测试锁住：
 * <ul>
 *   <li>新增了某种语言的 key，另一种语言漏翻 —— 那种语言的用户直接看到 key；</li>
 *   <li>新增了 {@link ResultCode} 却忘了补文案 —— 所有语言都看到 key。</li>
 * </ul>
 * <p>找不到 key 时返回 key 本身（{@code spring.messages.use-code-as-default-message=true}），
 * 这是刻意的：宁可显示得难看，也不要因为缺一条翻译把请求变成 500。
 */
class MessagesPropertiesTest {

  private static final String BASE = "i18n/messages.properties";
  private static final String EN = "i18n/messages_en_US.properties";

  @Test
  @DisplayName("两种语言的 key 集合完全一致")
  void keySetsMatch() {
    Set<String> baseKeys = keysOf(BASE);
    Set<String> enKeys = keysOf(EN);

    assertThat(baseKeys).as("%s 不应为空", BASE).isNotEmpty();
    assertThat(missingIn(enKeys, baseKeys)).as("英文缺少的 key").isEmpty();
    assertThat(missingIn(baseKeys, enKeys)).as("中文缺少的 key").isEmpty();
  }

  @Test
  @DisplayName("每个 ResultCode 的 key 都能在默认语言文件里找到")
  void everyResultCodeHasMessage() {
    Set<String> baseKeys = keysOf(BASE);

    for (ResultCode resultCode : ResultCode.values()) {
      assertThat(baseKeys)
          .as("ResultCode.%s 的 key=%s 未在 %s 中定义",
              resultCode.name(), resultCode.getMessageKey(), BASE)
          .contains(resultCode.getMessageKey());
    }
  }

  @Test
  @DisplayName("消息未初始化时返回 key 本身，而不是抛异常")
  void fallsBackToKeyWithoutMessageSource() {
    // 纯单测场景拿不到 Spring 上下文，此时应退化为 key，避免 NPE
    assertThat(Messages.get("error.bad.request")).isEqualTo("error.bad.request");
    assertThat(Messages.get(null)).isNull();
  }

  private Set<String> missingIn(Set<String> target, Set<String> reference) {
    Set<String> missing = new TreeSet<>(reference);
    missing.removeAll(target);
    return missing;
  }

  private Set<String> keysOf(String resource) {
    Properties properties = new Properties();
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(in).as("资源文件 %s 应存在", resource).isNotNull();
      // 必须显式用 UTF-8 读取：Properties 默认 ISO-8859-1，中文会乱码
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("读取 " + resource + " 失败", e);
    }
    Set<String> keys = new TreeSet<>();
    properties.stringPropertyNames().forEach(keys::add);
    return keys;
  }
}
