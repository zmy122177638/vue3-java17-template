package com.zmy.yorvix.security;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;

/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 * 由 AuthInterceptor 在请求进入时写入、请求结束时清理。
 */
public final class AuthContext {

  private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

  private AuthContext() {
  }

  public static void set(LoginUser user) {
    HOLDER.set(user);
  }

  /** 获取当前用户；未登录时抛出 401 业务异常 */
  public static LoginUser require() {
    LoginUser user = HOLDER.get();
    if (user == null) {
      throw new BizException(ResultCode.UNAUTHORIZED);
    }
    return user;
  }

  public static LoginUser getOrNull() {
    return HOLDER.get();
  }

  public static void clear() {
    HOLDER.remove();
  }
}
