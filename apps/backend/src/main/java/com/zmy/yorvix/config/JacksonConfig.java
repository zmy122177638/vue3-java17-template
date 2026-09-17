package com.zmy.yorvix.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 时间格式统一配置。
 * <p><b>为什么需要这个类</b>：{@code spring.jackson.date-format} 只作用于
 * {@code java.util.Date} / {@code Calendar}，对 JSR-310 类型（本项目实体统一使用
 * {@code LocalDateTime}）完全不生效——Jackson 为它们注册了独立的序列化器，
 * 不读取 {@code ObjectMapper} 上的 {@code DateFormat}。
 * <p>症状很隐蔽：配置看着写对了，但接口返回的是 ISO-8601（{@code 2026-09-15T17:49:32}，
 * 带 {@code T}），前端表格直接展示原始字符串。
 * <p>因此这里显式注册 {@code LocalDateTime} 的序列化器/反序列化器，
 * 使「后端产出格式」与「前端提交格式」一致，前端无需再做格式化。
 */
@Configuration
public class JacksonConfig {

  /** 全局统一的日期时间格式（与前端展示、请求体保持同一约定） */
  public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

  /**
   * 注册 LocalDateTime 的格式化器。
   * <p>使用 Spring Boot 4（Jackson 3）的 {@code JsonMapperBuilderCustomizer} 扩展点，
   * 而不是替换整个 {@code ObjectMapper}：后者会丢掉 Boot 已配置好的
   * {@code time-zone}、{@code FAIL_ON_UNKNOWN_PROPERTIES} 等默认行为。
   */
  @Bean
  JsonMapperBuilderCustomizer localDateTimeFormatCustomizer() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
    module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
    return builder -> builder.addModule(module);
  }
}
