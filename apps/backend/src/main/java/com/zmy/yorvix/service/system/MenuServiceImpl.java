package com.zmy.yorvix.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zmy.yorvix.common.api.ResultCode;
import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.MenuProperties;
import com.zmy.yorvix.mapper.system.SysMenuMapper;
import com.zmy.yorvix.mapper.system.SysRoleMapper;
import com.zmy.yorvix.mapper.system.SysRoleMenuMapper;
import com.zmy.yorvix.model.system.SysMenu;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.system.SysRoleMenu;
import com.zmy.yorvix.request.system.MenuSortRequest;
import com.zmy.yorvix.request.system.MenuUpsertRequest;
import com.zmy.yorvix.response.system.MenuNode;
import com.zmy.yorvix.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现。
 * <p>语言解析：lang 形如 zh-CN / en-US（来自请求头 Accept-Language），
 * title JSON 缺失对应语言时回退 zh-CN，再回退 JSON 中第一个值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

  private final SysMenuMapper menuMapper;
  private final SysRoleMapper roleMapper;
  private final SysRoleMenuMapper roleMenuMapper;
  private final MenuProperties menuProperties;
  private final ObjectMapper objectMapper;

  // ---------------- 路由下发 ----------------

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getMenuRoutes(LoginUser user, String lang) {
    List<SysMenu> menus = listVisibleMenus(user);
    if (menus.isEmpty()) {
      return List.of();
    }
    Set<Long> idSet = menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
    Map<Long, List<SysMenu>> byPid = menus.stream()
        .collect(Collectors.groupingBy(SysMenu::getPid));
    List<SysMenu> roots = menus.stream()
        .filter(m -> m.getPid() == null || m.getPid() == 0 || !idSet.contains(m.getPid()))
        .sorted(menuOrder())
        .toList();
    List<Map<String, Object>> routes = new ArrayList<>();
    for (SysMenu root : roots) {
      Map<String, Object> route = toRoute(root, byPid, lang);
      if (route != null) {
        routes.add(route);
      }
    }
    return routes;
  }

  /** 目录下无可见子菜单时跳过该目录（避免空节点） */
  private Map<String, Object> toRoute(SysMenu menu, Map<Long, List<SysMenu>> byPid, String lang) {
    String path = resolveRoutePath(menu);
    if (path == null) {
      return null;
    }
    Map<String, Object> route = new LinkedHashMap<>();
    route.put("name", menu.getName());
    route.put("path", path);
    if (SysMenu.TYPE_EMBEDDED.equals(menu.getType()) || SysMenu.TYPE_LINK.equals(menu.getType())) {
      // 内嵌页与外链统一交给前端 IFrameView 承接（link 型由前端按 meta.link 打开新窗口）
      route.put("component", IFRAME_VIEW);
    } else if (SysMenu.TYPE_MENU.equals(menu.getType())
        && StringUtils.hasText(menu.getComponent())) {
      route.put("component", menu.getComponent());
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("title", resolveTitle(menu.getTitle(), lang));
    if (StringUtils.hasText(menu.getIcon())) {
      meta.put("icon", menu.getIcon());
    }
    if (StringUtils.hasText(menu.getActiveIcon())) {
      meta.put("activeIcon", menu.getActiveIcon());
    }
    if (StringUtils.hasText(menu.getActivePath())) {
      meta.put("activePath", menu.getActivePath());
    }
    meta.put("order", menu.getSort());
    if (menu.getKeepAlive() != null && menu.getKeepAlive() == 1) {
      meta.put("keepAlive", true);
    }
    if (menu.getAffixTab() != null && menu.getAffixTab() == 1) {
      meta.put("affixTab", true);
    }
    if (StringUtils.hasText(menu.getBadgeType())) {
      meta.put("badgeType", menu.getBadgeType());
      meta.put("badge", menu.getBadge());
      meta.put("badgeVariants", menu.getBadgeVariants());
    }
    putFlag(meta, "hideInMenu", menu.getHideInMenu());
    putFlag(meta, "hideChildrenInMenu", menu.getHideChildrenInMenu());
    putFlag(meta, "hideInBreadcrumb", menu.getHideInBreadcrumb());
    putFlag(meta, "hideInTab", menu.getHideInTab());
    if (SysMenu.TYPE_EMBEDDED.equals(menu.getType())) {
      meta.put("iframeSrc", menu.getLinkSrc());
    }
    if (SysMenu.TYPE_LINK.equals(menu.getType())) {
      meta.put("link", menu.getLinkSrc());
    }
    route.put("meta", meta);

    if (SysMenu.TYPE_CATALOG.equals(menu.getType())) {
      List<Map<String, Object>> children = byPid
          .getOrDefault(menu.getId(), List.of())
          .stream()
          .sorted(menuOrder())
          .map(child -> toRoute(child, byPid, lang))
          .filter(Objects::nonNull)
          .toList();
      if (children.isEmpty()) {
        return null;
      }
      route.put("children", children);
    }
    return route;
  }

  /**
   * 解析下发用的路由地址。
   * <p>外链不需要填写路由地址（点击由 meta.link 直接打开外部地址），
   * 但前端菜单是从路由树生成的，因此这里按 id 派生一个稳定的占位地址；
   * 其他类型缺地址属于脏数据，跳过并告警，避免拖垮整棵菜单注册。
   */
  private String resolveRoutePath(SysMenu menu) {
    if (StringUtils.hasText(menu.getPath())) {
      return menu.getPath();
    }
    if (SysMenu.TYPE_LINK.equals(menu.getType())) {
      return EXTERNAL_PATH_PREFIX + menu.getId();
    }
    log.warn("菜单[id={}, name={}]缺少路由地址，已跳过下发", menu.getId(), menu.getName());
    return null;
  }

  /** 仅在开关为 true（1）时下发，避免无意义的 meta 噪音 */
  private void putFlag(Map<String, Object> meta, String key, Integer value) {
    if (value != null && value == 1) {
      meta.put(key, true);
    }
  }

  private Comparator<SysMenu> menuOrder() {
    return Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Integer::compareTo))
        .thenComparing(SysMenu::getId);
  }

  /** 可见菜单：启用 且 (管理员全量 | 角色授权范围内)，不含按钮 */
  private List<SysMenu> listVisibleMenus(LoginUser user) {
    LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
        .eq(SysMenu::getStatus, 1)
        .ne(SysMenu::getType, SysMenu.TYPE_BUTTON);
    if (isAdmin(user)) {
      return menuMapper.selectList(wrapper.orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
    }
    Set<Long> menuIds = authorizedMenuIds(user);
    if (menuIds.isEmpty()) {
      return List.of();
    }
    wrapper.in(SysMenu::getId, menuIds);
    return menuMapper.selectList(wrapper.orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
  }

  /**
   * 是否内置管理员。
   * <p>roles 已由 {@code AuthInterceptor} 在请求入口注入（超管在注入时已补 ADMIN 编码），
   * 与 {@link RoleService#isAdmin} 等价，因此这里不再查库。
   */
  private boolean isAdmin(LoginUser user) {
    return user.hasRole(RoleService.ADMIN_CODE);
  }

  // ---------------- 按钮权限码 ----------------

  @Override
  @Transactional(readOnly = true)
  public List<String> getAuthCodes(LoginUser user) {
    return List.copyOf(authCodesOf(user));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> authCodesOf(LoginUser user) {
    LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
        .eq(SysMenu::getStatus, 1)
        .eq(SysMenu::getType, SysMenu.TYPE_BUTTON)
        .isNotNull(SysMenu::getAuthCode);
    if (!isAdmin(user)) {
      Set<Long> menuIds = authorizedMenuIds(user);
      if (menuIds.isEmpty()) {
        return Set.of();
      }
      wrapper.in(SysMenu::getId, menuIds);
    }
    // 只取 auth_code 一列：判定用途不需要整行数据
    return menuMapper.selectList(wrapper.select(SysMenu::getAuthCode)).stream()
        .map(SysMenu::getAuthCode)
        .filter(StringUtils::hasText)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** 用户全部角色授权的菜单 ID 集合 */
  private Set<Long> authorizedMenuIds(LoginUser user) {
    List<String> codes = user.getRoles();
    if (codes == null || codes.isEmpty()) {
      return Set.of();
    }
    List<SysRole> roles = roleMapper.selectList(
        new LambdaQueryWrapper<SysRole>().in(SysRole::getCode, codes));
    if (roles.isEmpty()) {
      return Set.of();
    }
    List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
    return roleMenuMapper.selectList(
            new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
        .stream()
        .map(SysRoleMenu::getMenuId)
        .collect(Collectors.toSet());
  }

  // ---------------- 管理端 ----------------

  @Override
  @Transactional(readOnly = true)
  public List<MenuNode> getFullTree() {
    List<SysMenu> all = menuMapper.selectList(
        new LambdaQueryWrapper<SysMenu>()
            .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
    return buildNodes(all, 0L);
  }

  /**
   * 组装菜单树。先按 pid 分一次组再递归取子节点，
   * 避免原实现那种"每层都对全表扫一遍"的 O(n²) 行为（分组方式与 {@link #getMenuRoutes} 保持一致）。
   */
  private List<MenuNode> buildNodes(List<SysMenu> all, Long pid) {
    Map<Long, List<SysMenu>> byPid = all.stream()
        .collect(Collectors.groupingBy(
            menu -> menu.getPid() == null ? 0L : menu.getPid(),
            LinkedHashMap::new,
            Collectors.toList()));
    Set<Long> adminOnlyIds = adminOnlyMenuIds(all);
    return buildChildren(byPid, pid, adminOnlyIds);
  }

  private List<MenuNode> buildChildren(Map<Long, List<SysMenu>> byPid, Long pid,
      Set<Long> adminOnlyIds) {
    List<SysMenu> siblings = byPid.getOrDefault(pid, List.of());
    List<MenuNode> nodes = new ArrayList<>(siblings.size());
    for (SysMenu menu : siblings) {
      MenuNode node = MenuNode.of(menu);
      node.setAdminOnly(adminOnlyIds.contains(menu.getId()));
      node.setChildren(buildChildren(byPid, menu.getId(), adminOnlyIds));
      nodes.add(node);
    }
    return nodes;
  }

  // ---------------- ADMIN 独占菜单 ----------------

  /**
   * 计算 ADMIN 独占菜单的 id 集合（含全部后代）。
   * <p>独占范围由 {@code yorvix.menu.admin-only-paths} 按路由地址前缀声明，
   * 它与 /api/system/** 上的 {@code @RequiresRoles("ADMIN")} 是同一件事的两种表达：
   * 后端靠注解拦接口，前端靠这里下发的标记决定"授权树里不给勾"。
   * 二者共用同一配置源，因此不会出现"菜单能授权、接口却 403"的漂移。
   * <p>包级可见是为了让单测直接覆盖路径段边界（{@code /system} 命中
   * {@code /system/user} 但不应命中 {@code /systematic}）与子树继承。
   */
  Set<Long> adminOnlyMenuIds(List<SysMenu> menus) {
    List<String> patterns = menuProperties.getAdminOnlyPaths();
    if (patterns == null || patterns.isEmpty()) {
      return Set.of();
    }
    Map<Long, List<SysMenu>> byPid = menus.stream()
        .collect(Collectors.groupingBy(menu -> menu.getPid() == null ? 0L : menu.getPid()));
    Set<Long> result = new HashSet<>();
    for (SysMenu menu : menus) {
      if (matchesAdminOnlyPath(menu.getPath(), patterns)) {
        // 独占按子树整体生效：/system 独占，则 /system/user 及其按钮也一并独占
        collectSubtreeIds(byPid, menu.getId(), result);
      }
    }
    return result;
  }

  /**
   * 是否命中 ADMIN 独占范围。
   * <p>按**路径段边界**匹配而不是裸前缀：配置 {@code /system} 时，
   * 应命中 {@code /system} 与 {@code /system/user}，但不该误命中 {@code /systematic}
   * （后者是业务菜单，被静默标记为独占会很难排查）。
   */
  private boolean matchesAdminOnlyPath(String path, List<String> patterns) {
    if (!StringUtils.hasText(path)) {
      return false;
    }
    return patterns.stream()
        .filter(StringUtils::hasText)
        .map(pattern -> pattern.endsWith("/")
            ? pattern.substring(0, pattern.length() - 1)
            : pattern)
        .anyMatch(pattern -> pattern.isEmpty()
            || pattern.equals(path)
            || path.startsWith(pattern + "/"));
  }

  private void collectSubtreeIds(Map<Long, List<SysMenu>> byPid, Long id, Set<Long> result) {
    if (!result.add(id)) {
      return;
    }
    for (SysMenu child : byPid.getOrDefault(id, List.of())) {
      collectSubtreeIds(byPid, child.getId(), result);
    }
  }

  @Override
  @Transactional
  public boolean createMenu(MenuUpsertRequest request) {
    validatePid(request.getPid(), null);
    validateRequiredFields(request);
    SysMenu menu = new SysMenu();
    applyUpsert(menu, request);
    if (request.getSort() == null) {
      // 未指定排序时追加到同级末尾（列表默认按 sort 升序），后续由拖拽调整
      menu.setSort(nextSort(menu.getPid()));
    }
    menuMapper.insert(menu);
    return true;
  }

  @Override
  @Transactional
  public boolean updateMenu(Long id, MenuUpsertRequest request) {
    SysMenu menu = requireMenu(id);
    validatePid(request.getPid(), id);
    validateRequiredFields(request);
    applyUpsert(menu, request);
    menuMapper.updateById(menu);
    return true;
  }

  @Override
  @Transactional
  public boolean deleteMenu(Long id) {
    requireMenu(id);
    long children = menuMapper.selectCount(
        new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPid, id));
    if (children > 0) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.menu.children.exist");
    }
    roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    menuMapper.deleteById(id);
    return true;
  }

  @Override
  @Transactional
  public boolean sortMenus(MenuSortRequest request) {
    Long pid = request.getPid() == null ? 0L : request.getPid();
    List<Long> ids = request.getIds();
    Map<Long, SysMenu> siblings = menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPid, pid))
        .stream()
        .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (a, b) -> a));
    // 只允许同级排序：ids 必须正好覆盖该父级下的全部子节点
    if (ids.size() != siblings.size() || !siblings.keySet().containsAll(ids)) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.menu.sort.mismatch");
    }
    int sort = 1;
    for (Long id : ids) {
      menuMapper.update(null, new LambdaUpdateWrapper<SysMenu>()
          .eq(SysMenu::getId, id)
          .set(SysMenu::getSort, sort++));
    }
    return true;
  }

  @Override
  @Transactional
  public boolean replaceRoleMenus(Long roleId, List<Long> menuIds) {
    roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
    if (menuIds == null || menuIds.isEmpty()) {
      return true;
    }
    // 菜单表是树形配置数据、量很小，全量加载一次即可支撑下面全部校验
    Set<Long> resolved = resolveAuthorizedMenuIds(menuMapper.selectList(null), menuIds);
    if (resolved.isEmpty()) {
      log.warn("角色授权请求未包含任何可授权菜单，该角色授权已被清空: roleId={}", roleId);
      return true;
    }
    roleMenuMapper.insertBatch(resolved.stream()
        .map(menuId -> {
          SysRoleMenu binding = new SysRoleMenu();
          binding.setRoleId(roleId);
          binding.setMenuId(menuId);
          return binding;
        })
        .toList());
    return true;
  }

  /**
   * 把前端提交的菜单 ID 规范化为可直接写库的授权集合。写库前做四件事：
   * <ol>
   *   <li>去重 —— 重复 id 会撞 uk_role_menu 唯一键导致 500；</li>
   *   <li>校验存在性 —— 防止写入无效 menuId 产生脏授权；</li>
   *   <li>逐级补全祖先 —— 缺少父目录时，菜单下发会把子菜单当成顶级路由，
   *       而它的 path 是相对父级的，前端会注册到错误位置；</li>
   *   <li>剔除 ADMIN 独占菜单 —— 前端授权树已过滤，这里兜底防手工构造的请求。</li>
   * </ol>
   * <p>包级可见（而非 private）是为了让单测能直接覆盖这段纯逻辑、不依赖数据库：
   * 它出错时不会抛异常、只会让行为悄悄不对（授权少了/多了、路由挂错位置），
   * 是最需要回归保护的地方。
   *
   * @param all     全量菜单（树形配置数据）
   * @param menuIds 前端提交的原始 ID 列表
   * @return 规范化后的 ID 集合，保持提交顺序
   * @throws BizException 存在无效的菜单 ID
   */
  Set<Long> resolveAuthorizedMenuIds(List<SysMenu> all, List<Long> menuIds) {
    if (menuIds == null || menuIds.isEmpty()) {
      return new LinkedHashSet<>();
    }
    Map<Long, SysMenu> byId = all.stream()
        .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (a, b) -> a));
    Set<Long> adminOnlyIds = adminOnlyMenuIds(all);

    Set<Long> resolved = new LinkedHashSet<>();
    for (Long menuId : new LinkedHashSet<>(menuIds)) {
      SysMenu menu = byId.get(menuId);
      if (menu == null) {
        throw new BizException(ResultCode.BAD_REQUEST, "error.menu.invalid");
      }
      resolved.add(menuId);
      Long pid = menu.getPid();
      // add 返回 false 表示该节点已在集合中（含脏数据成环的情况），此时终止上溯；
      // 这也让"多个子菜单共享同一父目录"只走一次上溯
      while (pid != null && pid != 0L && byId.containsKey(pid) && resolved.add(pid)) {
        pid = byId.get(pid).getPid();
      }
    }

    int before = resolved.size();
    resolved.removeIf(adminOnlyIds::contains);
    if (resolved.size() < before) {
      log.warn("角色授权请求包含 ADMIN 独占菜单，已剔除 {} 个", before - resolved.size());
    }
    return resolved;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> getMenuIdsByRoleId(Long roleId) {
    return roleMenuMapper.selectList(
            new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
        .stream()
        .map(SysRoleMenu::getMenuId)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public UserMenuInfo resolveUserMenuInfo(LoginUser user) {
    // 只回答两件事：有没有菜单、有没有配置全局首页。
    // "第一个可访问页面"交给前端按**真实路由**计算（它同时掌握路由表与菜单顺序，
    // 见 apps/web-antd/src/router/home-path.ts），避免后端再维护一套路径拼装逻辑。
    boolean hasMenu = !listVisibleMenus(user).isEmpty();
    if (!hasMenu) {
      // 完全没有菜单时首页不参与判断，交给 permissionIssue=NO_MENU 那条链路提示用户
      return new UserMenuInfo(null, false);
    }
    String configured = StringUtils.hasText(menuProperties.getHomePath())
        ? menuProperties.getHomePath().trim()
        : null;
    return new UserMenuInfo(configured, true);
  }

  // ---------------- 工具 ----------------

  private void validatePid(Long pid, Long selfId) {
    if (pid == null || pid == 0) {
      return;
    }
    if (Objects.equals(pid, selfId)) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.menu.parent.self");
    }
    SysMenu parent = requireMenu(pid);
    if (selfId == null) {
      // 新增场景不存在环
      return;
    }
    // 沿父链上溯，禁止把节点挂到自己的子孙节点下：一旦成环，该子树从根不可达，
    // 树构建会静默丢弃它们，表现为「菜单凭空消失」且没有任何报错，极难排查
    Set<Long> seen = new HashSet<>();
    Long cursor = parent.getPid();
    while (cursor != null && cursor != 0L && seen.add(cursor)) {
      if (Objects.equals(cursor, selfId)) {
        throw new BizException(ResultCode.BAD_REQUEST, "error.menu.parent.descendant");
      }
      SysMenu node = menuMapper.selectById(cursor);
      cursor = node == null ? 0L : node.getPid();
    }
  }

  private SysMenu requireMenu(Long id) {
    SysMenu menu = menuMapper.selectById(id);
    if (menu == null) {
      throw new BizException(ResultCode.NOT_FOUND, "error.menu.not.found");
    }
    return menu;
  }

  private void applyUpsert(SysMenu menu, MenuUpsertRequest request) {
    menu.setPid(request.getPid() == null ? 0L : request.getPid());
    menu.setType(request.getType());
    menu.setName(request.getName());
    menu.setPath(request.getPath());
    menu.setComponent(request.getComponent());
    menu.setAuthCode(request.getAuthCode());
    menu.setTitle(request.getTitle());
    menu.setIcon(request.getIcon());
    menu.setActiveIcon(request.getActiveIcon());
    menu.setActivePath(request.getActivePath());
    menu.setLinkSrc(request.getLinkSrc());
    menu.setBadgeType(request.getBadgeType());
    menu.setBadge(request.getBadge());
    menu.setBadgeVariants(request.getBadgeVariants());
    menu.setHideInMenu(Boolean.TRUE.equals(request.getHideInMenu()) ? 1 : 0);
    menu.setHideChildrenInMenu(Boolean.TRUE.equals(request.getHideChildrenInMenu()) ? 1 : 0);
    menu.setHideInBreadcrumb(Boolean.TRUE.equals(request.getHideInBreadcrumb()) ? 1 : 0);
    menu.setHideInTab(Boolean.TRUE.equals(request.getHideInTab()) ? 1 : 0);
    // 排序由拖拽维护：未传时保留原值（新增场景在 createMenu 里落到同级末尾）
    if (request.getSort() != null) {
      menu.setSort(request.getSort());
    }
    menu.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    menu.setKeepAlive(Boolean.TRUE.equals(request.getKeepAlive()) ? 1 : 0);
    menu.setAffixTab(Boolean.TRUE.equals(request.getAffixTab()) ? 1 : 0);
  }

  /** 同级末尾的排序值（无子节点时从 1 开始） */
  private int nextSort(Long pid) {
    return menuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPid, pid == null ? 0L : pid))
        .stream()
        .map(SysMenu::getSort)
        .filter(Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(0) + 1;
  }

  /** 内嵌/外链必须带 http(s) 地址，否则前端无法承载 */
  private void validateRequiredFields(MenuUpsertRequest request) {
    String type = request.getType();
    // 目录/菜单/内嵌页都要注册为前端路由，路由地址（path）必填，否则前端 addRoute 会失败；
    // 按钮与外链不需要路由地址（外链由 meta.link 直接打开外部地址）
    if (!SysMenu.TYPE_BUTTON.equals(type) && !SysMenu.TYPE_LINK.equals(type)
        && !StringUtils.hasText(request.getPath())) {
      throw new BizException(ResultCode.BAD_REQUEST, "error.menu.path.required");
    }
    if (SysMenu.TYPE_EMBEDDED.equals(type) || SysMenu.TYPE_LINK.equals(type)) {
      if (!StringUtils.hasText(request.getLinkSrc())) {
        throw new BizException(ResultCode.BAD_REQUEST, "error.menu.link.required");
      }
    }
  }

  @Override
  public String resolveTitle(String titleJson, String lang) {
    if (!StringUtils.hasText(titleJson)) {
      return "";
    }
    String target = LANG_EN.equals(lang) ? LANG_EN : LANG_ZH;
    Map<String, String> map = parseTitle(titleJson);
    if (map.isEmpty()) {
      return titleJson;
    }
    String value = map.get(target);
    if (!StringUtils.hasText(value)) {
      value = map.get(LANG_ZH);
    }
    if (!StringUtils.hasText(value) && !map.isEmpty()) {
      value = map.values().iterator().next();
    }
    return value == null ? "" : value;
  }

  private Map<String, String> parseTitle(String titleJson) {
    try {
      return objectMapper.readValue(titleJson, new TypeReference<Map<String, String>>() {
      });
    } catch (Exception e) {
      log.debug("title 非多语言 JSON，按纯文本处理: {}", titleJson);
      return Map.of();
    }
  }

  @Override
  public SysMenu save(SysMenu menu) {
    menuMapper.insert(menu);
    return menu;
  }

  @Override
  public SysMenu findByName(String name) {
    return menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getName, name));
  }
}
