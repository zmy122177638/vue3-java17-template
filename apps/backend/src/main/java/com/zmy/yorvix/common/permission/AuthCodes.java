package com.zmy.yorvix.common.permission;

/**
 * 业务域按钮权限码的**唯一登记处**（当前为空——模板不自带业务模块，请按需登记）。
 *
 * <h2>为什么权限码要集中登记</h2>
 * 同一个字符串至少要在三处出现且必须完全一致：
 * <ol>
 *   <li>Controller 上的 {@code @RequiresPermissions}（接口级安全边界）；</li>
 *   <li>BUTTON 型菜单的 {@code auth_code}（授权数据来源，种子或菜单管理页写入）；</li>
 *   <li>前端按钮的 {@code v-access:code} / {@code VbenTableAction.auth}（UX 显隐）。</li>
 * </ol>
 * 前两处共用这里的常量，就不会出现"注解写了 {@code Report:Export}、种子里是
 * {@code Report:export}"这种极难排查的错位——症状是明明授权了却一直 403。
 * 前端无法共享 Java 常量，仍需手写字符串，但三者的对应关系应是显然的。
 *
 * <h2>怎么加一个权限码</h2>
 * <ol>
 *   <li>在这里加一行，命名形如 {@code 资源:动作}（如 {@code Report:Export}）；</li>
 *   <li>业务接口标注 {@code @RequiresPermissions(AuthCodes.REPORT_EXPORT)}；</li>
 *   <li>菜单管理页新增 BUTTON 型子菜单，{@code auth_code} 填同一字符串，并授权给目标角色；</li>
 *   <li>前端按钮挂 {@code v-access:code} 或 {@code auth} 数组，字符串保持一致。</li>
 * </ol>
 * 权限码的生效链路由 {@code AuthInterceptor}（懒加载查询）+ {@code MenuService#authCodesOf}
 * 承担，详见 {@code @RequiresPermissions} 的注释。
 *
 * <h2>不要在这里登记系统管理域的权限码</h2>
 * {@code /api/system/**} 是 ADMIN 独占（{@code @RequiresRoles("ADMIN")} 一刀切，且 ADMIN
 * 角色代码层全量放行），在那里声明 {@code System:User:Create} 之类的权限码**永远不会参与判定**，
 * 留在库里只会让人误以为按钮级权限在生效。
 */
public final class AuthCodes {

  private AuthCodes() {
  }
}
