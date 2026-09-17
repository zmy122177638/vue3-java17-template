package com.zmy.yorvix.common.api;

import lombok.Getter;

/**
 * 统一返回码。
 * <p><b>这里存的是 i18n key，不是最终文案</b>：文案在响应生成时按请求语言
 * （{@code Accept-Language}）解析，见 {@code common/i18n/Messages} 与
 * {@code resources/i18n/messages*.properties}。
 * <p>新增返回码时：先在这里加一条 + 赋值 key，再到两个 properties 文件里各补一行。
 * 漏补不会报错（找不到 key 时返回 key 本身），但用户会看到 {@code error.xxx} 这样的原文。
 * <p>{@code code} 是给程序判断用的稳定标识（前端可据此做分支），{@code messageKey} 只影响展示。
 * <p><b>值的语义分两段</b>：400~599 与 HTTP 状态码一一对应，{@code GlobalExceptionHandler}
 * 会把它们原样写进响应状态（网关与监控按状态码统计）；1000 起的业务码一律保持 HTTP 200，
 * 只靠响应体里的 {@code code} 区分——因为前端的 401 会触发"刷新令牌/重新登录"链路，
 * 而"密码错误""账号被锁定"这类业务失败绝不能走那条链路。
 * 唯一的例外是 {@link #TOKEN_INVALID}：它虽然是业务码，但语义就是 401。
 */
@Getter
public enum ResultCode {

  SUCCESS(0, "result.success"),

  BAD_REQUEST(400, "error.bad.request"),
  UNAUTHORIZED(401, "error.unauthorized"),
  FORBIDDEN(403, "error.forbidden"),
  NOT_FOUND(404, "error.not.found"),
  METHOD_NOT_ALLOWED(405, "error.method.not.allowed"),
  UNSUPPORTED_MEDIA_TYPE(415, "error.media.type.unsupported"),
  INTERNAL_ERROR(500, "error.internal"),

  // 业务码（1000 起）
  USERNAME_OR_PASSWORD_ERROR(1001, "error.login.failed"),
  ACCOUNT_DISABLED(1002, "error.account.disabled"),
  TOKEN_INVALID(1003, "error.token.invalid"),
  PASSWORD_ERROR(1005, "error.password.wrong"),
  USER_NOT_FOUND(1009, "error.user.not.found"),
  /** 登录失败次数超过上限，被临时锁定（{0} 为剩余分钟数） */
  LOGIN_LOCKED(1010, "error.login.locked");

  private final int code;
  private final String messageKey;

  ResultCode(int code, String messageKey) {
    this.code = code;
    this.messageKey = messageKey;
  }
}
