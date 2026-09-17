package com.zmy.yorvix.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 后端消息解析入口：把 i18n key 按**当前请求语言**解析为面向用户的文案。
 * <p><b>为什么用静态持有</b>：需要解析消息的地方有两类，都拿不到依赖注入——
 * <ol>
 *   <li>{@code Result} 的静态工厂（{@code Result.ok()} / {@code Result.fail(code)}）；</li>
 *   <li>{@code BizException} 的构造（在任意 service 里抛出）。</li>
 * </ol>
 * 替代方案是把 MessageSource 一层层往下传，或让每个调用点自己解析，
 * 前者污染所有签名、后者会漏。静态持有是这里更实用的取舍，
 * 且 {@link MessageSource} 本身线程安全，只在启动时赋值一次。
 * <p><b>语言来源</b>：{@link LocaleContextHolder} 由 Spring MVC 的
 * {@code AcceptHeaderLocaleResolver} 从请求头 {@code Accept-Language} 填充，
 * 前端 {@code api/request.ts} 已经在每个请求上带了这个头。
 * <p><b>找不到 key 时的行为</b>：{@code spring.messages.use-code-as-default-message=true}
 * 会让 MessageSource 返回 key 本身，因此不会抛 {@code NoSuchMessageException}
 * （否则缺一条翻译就变成 500）。未初始化的场景（如纯单测）同样返回 key。
 */
public final class Messages {

  private static volatile MessageSource messageSource;

  private Messages() {
  }

  /** 由 {@code MessageSourceConfig} 在启动时注入（仅应在启动阶段调用） */
  public static void setMessageSource(MessageSource source) {
    messageSource = source;
  }

  /**
   * 解析消息。
   *
   * @param key  i18n key（如 {@code error.username.exists}）；为空时原样返回
   * @param args 占位符参数，对应 properties 中的 {@code {0}}、{@code {1}}
   */
  public static String get(String key, Object... args) {
    if (key == null || key.isEmpty()) {
      return key;
    }
    MessageSource source = messageSource;
    if (source == null) {
      return key;
    }
    return source.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
