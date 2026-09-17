package com.zmy.yorvix.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限码鉴权注解：标注在 Controller 方法或类上，
 * 由 {@link AuthInterceptor} 校验当前登录用户是否持有**全部**指定权限码（AND 语义），
 * 不满足返回真实 HTTP 403。
 * <p>权限码来自 BUTTON 型菜单的 {@code auth_code}，命名形如 {@code 资源:动作}
 * （如 {@code Report:Export}），由「角色 → 菜单授权」（{@code sys_role_menu}）推导得出。
 * 常量登记在 {@code common/permission/AuthCodes}。
 * <p><b>与 {@link RequiresRoles} 的分工</b>：
 * <ul>
 *   <li>{@code @RequiresRoles} 用于**平台治理能力**（如 {@code /api/system/**} 要求 ADMIN），
 *       判定依据是用户持有的角色编码；</li>
 *   <li>{@code @RequiresPermissions} 用于**业务能力**，判定依据是被授权的按钮权限码，
 *       粒度更细，且能在角色管理页里可视化配置。</li>
 * </ul>
 * <p><b>这一层才是业务域的安全边界。</b>前端 {@code VbenTableAction.auth} /
 * {@code v-access:code} 只是同一份数据的 UI 呈现，隐藏按钮不等于拒绝请求——
 * 手工构造请求同样会被这里拦下。
 * <p><b>开销</b>：拦截器里是懒加载的，只有命中本注解的接口才会去查一次权限码，
 * 未标注的接口零额外开销。
 * <p>ADMIN 角色与超管账号代码层全量放行（不查授权表），与 {@link RequiresRoles} 语义一致。
 *
 * @see RequiresRoles
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermissions {

  /** 需要持有的权限码（全部满足） */
  String[] value();
}
