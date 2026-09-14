package com.zmy.yorvix.common.api;

import lombok.Getter;

/**
 * 统一响应体：{ code, message, data, timestamp }
 */
@Getter
public class Result<T> {

  private final int code;
  private final String message;
  private final T data;
  private final long timestamp;

  private Result(int code, String message,  T data) {
    this.code = code;
    this.message = message;
    this.data = data;
    this.timestamp = System.currentTimeMillis();
  }

  public static <T> Result<T> ok() {
    return ok(null);
  }

  public static <T> Result<T> ok(T data) {
    return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
  }

  public static <T> Result<T> fail(ResultCode resultCode) {
    return fail(resultCode, resultCode.getMessage());
  }

  public static <T> Result<T> fail(ResultCode resultCode, String message) {
    return new Result<>(resultCode.getCode(), message, null);
  }

  public static <T> Result<T> fail(int code, String message) {
    return new Result<>(code, message, null);
  }
}
