package com.zmy.yorvix.common.exception;

import com.zmy.yorvix.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：抛出后由 {@code GlobalExceptionHandler} 按请求语言解析为面向用户的文案。
 * <p><b>注意 message 是 i18n key，不是最终文案</b>（如 {@code error.username.exists}）。
 * 这是刻意的：异常在任意 service 里抛出，而"用哪种语言回复"取决于这次请求的
 * {@code Accept-Language}，只有到响应生成时才知道。因此异常只携带 key + 参数，
 * 由 {@code GlobalExceptionHandler} 统一解析。
 * <p>副作用是日志里看到的是 key（可搜索、不受语言影响，反而更好排查）；
 * 需要看具体文案时对照 {@code resources/i18n/messages*.properties}。
 * <p>用法：
 * <pre>
 *   throw new BizException(ResultCode.BAD_REQUEST, "error.username.exists");
 *   throw new BizException(ResultCode.BAD_REQUEST, "error.role.disabled", roleName);
 * </pre>
 */
@Getter
public class BizException extends RuntimeException {

  private final int code;

  /** i18n 占位符参数，对应 properties 中的 {@code {0}}、{@code {1}} */
  private final Object[] args;

  public BizException(ResultCode resultCode) {
    this(resultCode.getCode(), resultCode.getMessageKey());
  }

  public BizException(ResultCode resultCode, String messageKey, Object... args) {
    this(resultCode.getCode(), messageKey, args);
  }

  public BizException(int code, String messageKey, Object... args) {
    super(messageKey);
    this.code = code;
    this.args = args;
  }
}
