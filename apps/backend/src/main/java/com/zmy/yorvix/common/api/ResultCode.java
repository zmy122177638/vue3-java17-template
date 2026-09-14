package com.zmy.yorvix.common.api;

import lombok.Getter;

/**
 * 统一返回码（通用登录模板，仅认证相关）。
 */
@Getter
public enum ResultCode {

  SUCCESS(0, "成功"),

  BAD_REQUEST(400, "请求参数错误"),
  UNAUTHORIZED(401, "未登录或登录已失效"),
  FORBIDDEN(403, "无权限访问"),
  NOT_FOUND(404, "资源不存在"),
  METHOD_NOT_ALLOWED(405, "请求方法不支持"),
  INTERNAL_ERROR(500, "系统内部错误"),

  // 业务码（1000 起）
  USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
  ACCOUNT_DISABLED(1002, "账号已被禁用"),
  TOKEN_INVALID(1003, "令牌无效或已过期"),
  PASSWORD_ERROR(1005, "原密码错误"),
  USER_NOT_FOUND(1009, "用户不存在");

  private final int code;
  private final String message;

  ResultCode(int code, String message) {
    this.code = code;
    this.message = message;
  }
}
