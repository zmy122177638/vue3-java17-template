package com.zmy.yorvix.config;

import com.zmy.yorvix.common.i18n.Messages;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;

/**
 * 把 Spring 的 {@link MessageSource} 交给 {@link Messages}，让静态调用点也能解析 i18n key。
 * <p>MessageSource 本身由 Spring Boot 依据 {@code spring.messages.*} 自动配置
 * （见 application.yaml：basename=i18n/messages、encoding=UTF-8、
 * fallback-to-system-locale=false、use-code-as-default-message=true）。
 * <p>这里只做一次赋值，不替换任何自动配置的行为。
 */
@Configuration
public class MessageSourceConfig {

  public MessageSourceConfig(MessageSource messageSource) {
    Messages.setMessageSource(messageSource);
  }
}
