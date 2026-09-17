package com.zmy.yorvix.security;

import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;

/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 * <p><b>写入方</b>：{@link AuthInterceptor}（鉴权通过后写入）。
 * <b>清理方</b>：{@link AuthContextFilter}（请求开始与结束各清一次）。
 * <p>清理**不要**放回拦截器的 {@code afterCompletion}：拦截器返回 false 时
 * Spring 不会调用它，值会留在复用的工作线程上（详见 {@link AuthContextFilter} 的注释）。
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
