package com.zmy.yorvix.common.api;

import com.zmy.yorvix.common.i18n.Messages;
import lombok.Getter;

/**
 * 统一响应体：{ code, message, data, timestamp }
 * <p>{@code message} 是按当前请求语言解析后的**最终文案**（前端直接展示即可）。
 * 解析规则见 {@link Messages}。
 */
@Getter
public class Result<T> {

  private final int code;
  private final String message;
  private final T data;
  private final long timestamp;

  private Result(int code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
    this.timestamp = System.currentTimeMillis();
  }

  public static <T> Result<T> ok() {
    return ok(null);
  }

  public static <T> Result<T> ok(T data) {
    return new Result<>(ResultCode.SUCCESS.getCode(),
        Messages.get(ResultCode.SUCCESS.getMessageKey()), data);
  }

  /** 按当前请求语言解析 {@link ResultCode} 对应的文案 */
  public static <T> Result<T> fail(ResultCode resultCode) {
    return fail(resultCode.getCode(), Messages.get(resultCode.getMessageKey()));
  }

  /**
   * 使用**已解析**的文案构造失败响应。
   * <p>供 {@code GlobalExceptionHandler} 与 {@code AuthInterceptor} 使用
   * （它们已经把 key 解析过了，或文案来自业务异常的动态参数）。
   */
  public static <T> Result<T> fail(int code, String message) {
    return new Result<>(code, message, null);
  }
}
