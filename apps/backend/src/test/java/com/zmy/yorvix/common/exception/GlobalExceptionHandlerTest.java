package com.zmy.yorvix.common.exception;

import com.zmy.yorvix.common.api.Result;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.security.OperationLogInterceptor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理：HTTP 状态码对齐 + 审计原因回传。
 *
 * <h2>为什么状态码必须锁死</h2>
 * 这里的映射同时决定了三件事：网关/监控如何统计错误率、前端是否触发令牌刷新、审计日志判定成败。
 * 尤其两条容易写错：
 * <ul>
 *   <li><b>业务码必须保持 HTTP 200</b>（如 1001 密码错误）。如果映射成 401，
 *       前端会把"密码错误"当成"令牌过期"去刷新令牌并跳登录页；</li>
 *   <li><b>TOKEN_INVALID 必须是 401</b>，否则前端 {@code doRefreshToken} 会把
 *       {@code Result} 对象当成 token 存起来，后续请求全部带上垃圾令牌。</li>
 * </ul>
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  @DisplayName("业务码（1001 密码错误等）保持 HTTP 200，由响应体 code 表达")
  void businessCodesStayHttp200() {
    Result<Void> result = handler.handleBiz(
        new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR), response, request);

    assertThat(result.getCode()).isEqualTo(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode());
    // 关键：不能变成 401，否则前端会走"刷新令牌/重新登录"链路
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("TOKEN_INVALID 映射为 401（前端据此刷新令牌）")
  void tokenInvalidMapsTo401() {
    handler.handleBiz(new BizException(ResultCode.TOKEN_INVALID), response, request);

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("参数类错误映射为真实 HTTP 状态码，并回传 i18n key 给审计")
  void parameterErrorsMapToRealStatusCodes() {
    handler.handleBiz(new BizException(ResultCode.BAD_REQUEST, "error.username.exists"), response,
        request);
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(request.getAttribute(OperationLogInterceptor.ERROR_ATTRIBUTE))
        .isEqualTo("error.username.exists");

    MockHttpServletResponse notFound = new MockHttpServletResponse();
    handler.handleNotFound(null, notFound, new MockHttpServletRequest());
    assertThat(notFound.getStatus()).isEqualTo(404);

    MockHttpServletResponse methodNotAllowed = new MockHttpServletResponse();
    handler.handleMethodNotSupported(null, methodNotAllowed, new MockHttpServletRequest());
    assertThat(methodNotAllowed.getStatus()).isEqualTo(405);

    MockHttpServletResponse mediaType = new MockHttpServletResponse();
    handler.handleMediaTypeNotSupported(null, mediaType, new MockHttpServletRequest());
    assertThat(mediaType.getStatus()).isEqualTo(415);
  }

  @Test
  @DisplayName("参数类型不匹配（?status=abc）返回 400，而不是落进 500 兜底")
  void typeMismatchMapsTo400() {
    Result<Void> result = handler.handleTypeMismatch(new TypeMismatchException("abc", Integer.class),
        response, request);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(result.getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
  }

  @Test
  @DisplayName("字段校验失败：拼上字段名、返回 400，并记录审计原因")
  void validationFailureKeepsFieldName() {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "userCreateRequest");
    bindingResult.addError(new FieldError("userCreateRequest", "username", "must not be blank"));

    Result<Void> result = handler.handleBind(new BindException(bindingResult), response, request);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(result.getMessage()).contains("username");
    assertThat(request.getAttribute(OperationLogInterceptor.ERROR_ATTRIBUTE))
        .isEqualTo(ResultCode.BAD_REQUEST.getMessageKey());
  }

  @Test
  @DisplayName("方法参数约束失败：文案由校验器按语言插值，直接透传并返回 400")
  void constraintViolationIsPassedThrough() {
    Result<Void> result = handler.handleConstraint(
        new ConstraintViolationException("password 长度需在 6-32 之间",
            Set.<ConstraintViolation<?>>of()),
        response, request);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(result.getMessage()).isEqualTo("password 长度需在 6-32 之间");
  }

  @Test
  @DisplayName("未预期异常：返回 500 + 通用文案（不泄露堆栈/表名）")
  void unexpectedExceptionMapsTo500WithGenericMessage() {
    Result<Void> result = handler.handleOther(new IllegalStateException("SQL: select * from x"),
        response, request);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(result.getCode()).isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
    assertThat(result.getMessage()).doesNotContain("SQL");
    assertThat(request.getAttribute(OperationLogInterceptor.ERROR_ATTRIBUTE))
        .isEqualTo(ResultCode.INTERNAL_ERROR.getMessageKey());
  }
}
