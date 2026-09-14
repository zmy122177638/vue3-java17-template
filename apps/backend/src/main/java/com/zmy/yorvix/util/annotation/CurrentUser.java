package com.zmy.yorvix.util.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 控制器参数注解：将当前登录用户注入 LoginUser 参数。
 * <pre>public Result&lt;Void&gt; me(@CurrentUser LoginUser user)</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
