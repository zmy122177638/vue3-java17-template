package com.zmy.yorvix.common.exception;

import com.zmy.yorvix.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：抛出后由 GlobalExceptionHandler 统一转为 Result 响应。
 */
@Getter
public class BizException extends RuntimeException {

  private final int code;

  public BizException(ResultCode resultCode) {
    super(resultCode.getMessage());
    this.code = resultCode.getCode();
  }

  public BizException(ResultCode resultCode, String message) {
    super(message);
    this.code = resultCode.getCode();
  }

  public BizException(int code, String message) {
    super(message);
    this.code = code;
  }
}
