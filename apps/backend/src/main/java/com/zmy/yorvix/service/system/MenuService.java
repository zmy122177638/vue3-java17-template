package com.zmy.yorvix.service.system;

import com.zmy.yorvix.model.system.SysMenu;
import com.zmy.yorvix.request.system.MenuSortRequest;
import com.zmy.yorvix.request.system.MenuUpsertRequest;
import com.zmy.yorvix.response.system.MenuNode;
import com.zmy.yorvix.security.LoginUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单服务：路由菜单下发、按钮权限码、管理端全量树与 CRUD。
 */
public interface MenuService {

  /** 支持的语言 */
  String LANG_ZH = "zh-CN";
  String LANG_EN = "en-US";

  /** 前端布局映射 key：内嵌页/外链统一由 IFrameView 承载（#/router/access.ts layoutMap） */
  String IFRAME_VIEW = "IFrameView";

  /**
   * 外链型菜单未填路由地址时派生占位地址的前缀（/external/{id}）。
   * 前端菜单由路由树生成，外链需要占一个路由节点，但点击时按 meta.link 直接打开外部地址。
   */
  String EXTERNAL_PATH_PREFIX = "/external/";

  /**
   * 下发路由菜单树（vben RouteRecordStringComponent 结构，mixed 模式由前端
   * fetchMenuListAsync 拉取后与本地路由合并）。
   * <ul>
   *   <li>ADMIN：全部启用的目录/菜单（内置管理员全量放行）；</li>
   *   <li>其他角色：仅下发 sys_role_menu 授权范围内启用的目录/菜单。</li>
   * </ul>
   *
   * @param user 当前登录用户（roles 已由 AuthInterceptor 注入，避免为判管理员身份重复查库）
   * @param lang 语言（zh-CN / en-US），用于解析 title 多语言 JSON
   */
  List<Map<String, Object>> getMenuRoutes(LoginUser user, String lang);

  /**
   * 按钮权限码集合（BUTTON 型菜单的 auth_code），供前端 v-access:code 使用。
   * ADMIN 全量；其他角色按授权。
   */
  List<String> getAuthCodes(LoginUser user);

  /**
   * 当前用户持有的按钮权限码集合，用于 {@code @RequiresPermissions} 的接口级鉴权。
   * <p>与 {@link #getAuthCodes} 返回同一份数据（前者是它的列表视图），
   * 拆成两个方法是为了让语义各自清楚：一个是"下发给前端的顺序列表"，
   * 一个是"服务端做包含判定的集合"。
   * <p><b>每次调用都会查库</b>，仅在需要判定时调用（拦截器里是懒加载，非全量请求都走）。
   */
  Set<String> authCodesOf(LoginUser user);

  /** 管理端：全量菜单树（含按钮、含禁用项） */
  List<MenuNode> getFullTree();

  boolean createMenu(MenuUpsertRequest request);

  boolean updateMenu(Long id, MenuUpsertRequest request);

  /** 删除菜单（有子节点时拒绝），并级联清理角色授权关联 */
  boolean deleteMenu(Long id);

  /** 同级拖拽排序（按 ids 顺序重排 pid 下的兄弟节点） */
  boolean sortMenus(MenuSortRequest request);

  /** 重置角色-菜单授权（全量替换） */
  boolean replaceRoleMenus(Long roleId, List<Long> menuIds);

  /** 查询角色已授权的菜单 ID 列表 */
  List<Long> getMenuIdsByRoleId(Long roleId);

  /**
   * 用户菜单摘要（供 /api/user/info 使用）。
   *
   * @param homePath 全局统一首页配置（{@code yorvix.menu.home-path}），未配置为 null；
   *                 "第一个可访问页面"由前端按真实路由计算，后端不再下发
   * @param hasMenu  是否存在可见菜单（用于区分"角色没授权任何菜单"与"有菜单但没有可作首页的页面"）
   */
  record UserMenuInfo(String homePath, boolean hasMenu) {
  }

  /**
   * 计算用户菜单摘要：是否有可见菜单 + 全局首页配置（不做任何路径计算）。
   * <p>前端取值优先级：全局配置 → 菜单树前序第一个可落地页面 → {@code preferences.app.defaultHomePath}。
   * <p>用户完全没有任何菜单时 {@code hasMenu=false} 且首页恒为 null（由前端落到"暂无权限"提示页）。
   */
  UserMenuInfo resolveUserMenuInfo(LoginUser user);

  /** 解析多语言 title JSON：{"zh-CN":"系统管理","en-US":"System"} -> 指定语言文本 */
  String resolveTitle(String titleJson, String lang);

  /** 供 DataInitializer 等直接写入菜单行 */
  SysMenu save(SysMenu menu);

  /** 按路由名查询菜单（种子数据幂等判断用），不存在返回 null */
  SysMenu findByName(String name);
}
