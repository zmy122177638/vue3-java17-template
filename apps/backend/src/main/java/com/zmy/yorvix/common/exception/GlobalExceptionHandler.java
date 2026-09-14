package com.zmy.yorvix.common.exception;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.api.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一转换为 Result 结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BizException.class)
  public Result<Void> handleBiz(BizException e, HttpServletResponse response) {
    log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
    applyHttpStatus(e.getCode(), response);
    return Result.fail(e.getCode(), e.getMessage());
  }

  /** 401/403 及令牌失效同步为真实 HTTP 状态码，前端据此触发重新认证 */
  private void applyHttpStatus(int code, HttpServletResponse response) {
    if (code == ResultCode.UNAUTHORIZED.getCode()
        || code == ResultCode.TOKEN_INVALID.getCode()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    } else if (code == ResultCode.FORBIDDEN.getCode()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<Void> handleValidation(MethodArgumentNotValidException e) {
    FieldError fe = e.getBindingResult().getFieldError();
    String msg = fe == null ? ResultCode.BAD_REQUEST.getMessage() : fe.getField() + " " + fe.getDefaultMessage();
    return Result.fail(ResultCode.BAD_REQUEST, msg);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public Result<Void> handleConstraint(ConstraintViolationException e) {
    return Result.fail(ResultCode.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
    return Result.fail(ResultCode.BAD_REQUEST, "请求体格式错误");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    return Result.fail(ResultCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public Result<Void> handleNotFound(NoResourceFoundException e) {
    return Result.fail(ResultCode.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleOther(Exception e) {
    log.error("系统异常", e);
    return Result.fail(ResultCode.INTERNAL_ERROR);
  }
}
