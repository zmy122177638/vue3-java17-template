package com.zmy.yorvix.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级角色鉴权注解：标注在 Controller 方法或类上，
 * 由 AuthInterceptor 校验当前登录用户是否持有全部指定角色（AND 语义），
 * 不满足返回 403。
 * <p>注意：本注解是接口层的安全边界；按钮级权限码（v-access:code）仅控制前端显隐。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRoles {

  /** 需要持有的角色编码（全部满足） */
  String[] value();
}
