package com.zmy.yorvix.common.exception;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.i18n.Messages;
import com.zmy.yorvix.security.OperationLogInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;

/**
 * 全局异常处理：统一转换为 Result 结构，并把 i18n key 解析为当前请求语言的文案。
 * <p><b>这里是"文案落地"的唯一出口</b>：业务代码只抛 key（{@code BizException}），
 * 由这里按 {@code Accept-Language} 解析。因此全站的错误提示语言与
 * 请求头声明的语言一致，前端不需要维护"错误码 → 文案"的映射表。
 *
 * <h2>HTTP 状态码对齐</h2>
 * 与 HTTP 状态码同值的返回码（400/401/403/404/405/415/500）会同步写进响应状态，
 * 让网关、监控、告警能按状态码统计——否则"参数类型传错"这类客户端错误会落进
 * 500 的兜底分支，把真正的服务端故障淹没。
 * <p>{@code TOKEN_INVALID} 虽然属于业务码，但语义就是 401，前端据此触发令牌刷新，
 * 因此也映射为 401。
 * <p><b>其余业务码（1000 起）一律保持 HTTP 200</b>：它们靠响应体里的 {@code code} 判定，
 * 尤其是"密码错误""账号被锁定"绝不能用 401 —— 那会让前端误以为令牌过期而触发
 * 刷新/重新登录链路。
 *
 * <h2>与审计日志的约定</h2>
 * 异常被本类消化后，{@code OperationLogInterceptor.afterCompletion} 拿到的
 * {@code ex} 是 null，仅凭状态码无法区分"改成了"与"业务校验不通过"
 * （后者是 HTTP 200 + 业务码）。因此这里把失败原因的 i18n key 写入请求属性
 * {@link OperationLogInterceptor#ERROR_ATTRIBUTE}，作为操作日志判定成败的依据。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BizException.class)
  public Result<Void> handleBiz(BizException e, HttpServletResponse response,
      HttpServletRequest request) {
    // 日志打 key 而不是解析后的文案：key 与语言无关、可搜索，排查时再对照 messages 文件
    log.warn("业务异常: code={}, key={}, args={}", e.getCode(), e.getMessage(),
        Arrays.toString(e.getArgs()));
    publishAuditError(request, e.getMessage());
    applyHttpStatus(e.getCode(), response);
    return Result.fail(e.getCode(), Messages.get(e.getMessage(), e.getArgs()));
  }

  /**
   * 请求体字段校验失败。
   * <p>具体文案来自约束注解（{@code @NotBlank} / {@code @Size} / ...）对应的
   * i18n key（见 {@code i18n/messages*.properties} 里的
   * {@code jakarta.validation.constraints.*.message}），由校验器按请求语言插值完成。
   * <p>这里额外拼上字段名，便于定位"是哪个字段不合格"——字段的**业务名称**属于前端
   * （前端表单已有同名内联校验与更友好的提示）。
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<Void> handleValidation(MethodArgumentNotValidException e,
      HttpServletResponse response, HttpServletRequest request) {
    String message = firstFieldMessage(e.getBindingResult().getFieldError());
    return badRequest(message, response, request);
  }

  /** 表单/查询参数绑定校验失败（非 JSON 请求体）：与上面的处理保持一致 */
  @ExceptionHandler(BindException.class)
  public Result<Void> handleBind(BindException e, HttpServletResponse response,
      HttpServletRequest request) {
    return badRequest(firstFieldMessage(e.getBindingResult().getFieldError()), response, request);
  }

  /** 方法参数上的约束校验失败：{@code e.getMessage()} 已按请求语言插值完成，直接透传 */
  @ExceptionHandler(ConstraintViolationException.class)
  public Result<Void> handleConstraint(ConstraintViolationException e,
      HttpServletResponse response, HttpServletRequest request) {
    return badRequest(e.getMessage(), response, request);
  }

  /**
   * 参数类型不匹配（如 {@code ?status=abc} 传给 Integer）。
   * <p>这类是**客户端错误**，必须返回 400；落进兜底的 500 会让错误日志与告警失真。
   */
  @ExceptionHandler(TypeMismatchException.class)
  public Result<Void> handleTypeMismatch(TypeMismatchException e, HttpServletResponse response,
      HttpServletRequest request) {
    String name = e.getPropertyName() == null ? "parameter" : e.getPropertyName();
    return badRequest(name + " " + Messages.get(ResultCode.BAD_REQUEST.getMessageKey()), response,
        request);
  }

  /** 缺少必填请求参数 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public Result<Void> handleMissingParameter(MissingServletRequestParameterException e,
      HttpServletResponse response, HttpServletRequest request) {
    return badRequest(e.getParameterName() + " "
        + Messages.get(ResultCode.BAD_REQUEST.getMessageKey()), response, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Result<Void> handleNotReadable(HttpMessageNotReadableException e,
      HttpServletResponse response, HttpServletRequest request) {
    return badRequest(Messages.get("error.body.unreadable"), response, request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
      HttpServletResponse response, HttpServletRequest request) {
    publishAuditError(request, ResultCode.METHOD_NOT_ALLOWED.getMessageKey());
    applyHttpStatus(ResultCode.METHOD_NOT_ALLOWED.getCode(), response);
    return Result.fail(ResultCode.METHOD_NOT_ALLOWED);
  }

  /** 请求体/请求头的媒体类型不受支持（如把 form-data 发给 JSON 接口） */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e,
      HttpServletResponse response, HttpServletRequest request) {
    publishAuditError(request, ResultCode.UNSUPPORTED_MEDIA_TYPE.getMessageKey());
    applyHttpStatus(ResultCode.UNSUPPORTED_MEDIA_TYPE.getCode(), response);
    return Result.fail(ResultCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public Result<Void> handleNotFound(NoResourceFoundException e, HttpServletResponse response,
      HttpServletRequest request) {
    applyHttpStatus(ResultCode.NOT_FOUND.getCode(), response);
    return Result.fail(ResultCode.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleOther(Exception e, HttpServletResponse response,
      HttpServletRequest request) {
    // 未预期异常只对外给通用文案：堆栈里常含表名、SQL、类名，不能返回给客户端
    log.error("系统异常", e);
    publishAuditError(request, ResultCode.INTERNAL_ERROR.getMessageKey());
    applyHttpStatus(ResultCode.INTERNAL_ERROR.getCode(), response);
    return Result.fail(ResultCode.INTERNAL_ERROR);
  }

  // ---------------- 内部 ----------------

  /** 客户端错误统一出口：对齐 HTTP 400 + 记录审计原因 */
  private Result<Void> badRequest(String message, HttpServletResponse response,
      HttpServletRequest request) {
    log.warn("请求参数校验失败: {}", message);
    publishAuditError(request, ResultCode.BAD_REQUEST.getMessageKey());
    applyHttpStatus(ResultCode.BAD_REQUEST.getCode(), response);
    return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
  }

  private String firstFieldMessage(FieldError fieldError) {
    return fieldError == null
        ? Messages.get(ResultCode.BAD_REQUEST.getMessageKey())
        : fieldError.getField() + " " + fieldError.getDefaultMessage();
  }

  /** 把失败原因的 i18n key 交给操作日志拦截器（见类注释的「与审计日志的约定」） */
  private void publishAuditError(HttpServletRequest request, String key) {
    if (request != null) {
      request.setAttribute(OperationLogInterceptor.ERROR_ATTRIBUTE, key);
    }
  }

  /**
   * 返回码 -> HTTP 状态码。
   * <p>只映射语义明确的那几个（与 HTTP 同值）+ TOKEN_INVALID（前端据此刷新令牌）；
   * 其余业务码保持 200，由响应体的 {@code code} 表达。
   */
  private void applyHttpStatus(int code, HttpServletResponse response) {
    if (code == ResultCode.UNAUTHORIZED.getCode()
        || code == ResultCode.TOKEN_INVALID.getCode()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    } else if (code == ResultCode.FORBIDDEN.getCode()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    } else if (code == ResultCode.BAD_REQUEST.getCode()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    } else if (code == ResultCode.NOT_FOUND.getCode()) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } else if (code == ResultCode.METHOD_NOT_ALLOWED.getCode()) {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else if (code == ResultCode.UNSUPPORTED_MEDIA_TYPE.getCode()) {
      response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
    } else if (code == ResultCode.INTERNAL_ERROR.getCode()) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
