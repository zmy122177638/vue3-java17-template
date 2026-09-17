package com.zmy.yorvix.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmy.yorvix.mapper.system.SysRoleMapper;
import com.zmy.yorvix.mapper.system.SysUserRoleMapper;
import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.system.SysMenu;
import com.zmy.yorvix.model.system.SysRole;
import com.zmy.yorvix.model.system.SysUserRole;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.security.PasswordEncoder;
import com.zmy.yorvix.service.system.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动初始化种子数据（角色、菜单树、账号及绑定）。
 * <p>所有步骤逐项幂等（先查后插）：无论数据库处于全新、部分旧数据（如仅有
 * 历史版本种子账号）等状态，均可安全补齐，不会因唯一键冲突导致启动失败。
 * <p>内置账号：{@code admin}（超管 {@code is_super=1} + 管理员角色 ADMIN），
 * 密码取 {@code yorvix.init.seed-password}（默认 123456，仅供本地开发）。
 * <p><b>只播种系统管理域</b>：业务角色、业务菜单、权限码按钮一律不预置——
 * 它们属于使用者自己的业务，种子数据无法猜测。新增业务模块时在本类里补
 * {@code seedXxx} 方法，并保持幂等（先查后插）。
 * <p><b>生产环境请激活 prod profile 关闭本初始化</b>；若忘记关闭，{@link StartupChecks}
 * 会在启动日志里打出告警并列出需要设置的变量。
 * <p>执行顺序排在 {@link StartupChecks} 之后：结构不符时先给出清晰的迁移指引，
 * 而不是让建号步骤抛出一堆裸 SQL 异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(prefix = "yorvix.init", name = "seed-enabled",
    havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

  /** 多语言 title：zh-CN / en-US */
  private static String title(String zh, String en) {
    return "{\"zh-CN\":\"" + zh + "\",\"en-US\":\"" + en + "\"}";
  }

  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final MenuService menuService;
  private final PasswordEncoder passwordEncoder;
  private final TokenProperties tokenProperties;

  @Override
  @Transactional
  public void run(String... args) {
    SysRole adminRole = ensureRole("ADMIN", "管理员",
        "内置管理员角色：代码层全量放行，不依赖 sys_role_menu，不可删除");

    seedSystemMenus();

    // 密码来自配置，不再硬编码：本地默认值由 StartupChecks 负责告警
    ensureUser("admin", tokenProperties.getInit().getSeedPassword(), "管理员", adminRole, true);
    log.info("种子数据检查完成：角色 ADMIN、系统管理菜单树、账号 admin");
  }

  // ---------------- 角色 / 账号（幂等） ----------------

  private SysRole ensureRole(String code, String name, String remark) {
    SysRole role = roleMapper.selectOne(
        new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
    if (role != null) {
      return role;
    }
    role = new SysRole();
    role.setCode(code);
    role.setName(name);
    role.setRemark(remark);
    roleMapper.insert(role);
    return role;
  }

  /**
   * 幂等创建内置账号；superUser=true 时同时校准 is_super=1。
   * <p>超管标识只由种子/改库维护（接口层不允许修改），这里做一次校准，
   * 避免被误改后系统失去兜底管理入口。
   */
  private void ensureUser(String username, String rawPassword, String nickname, SysRole role,
      boolean superUser) {
    SysUser user = userMapper.selectOne(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    if (user == null) {
      user = new SysUser();
      user.setUsername(username);
      user.setPasswordHash(passwordEncoder.encode(rawPassword));
      user.setNickname(nickname);
      user.setStatus(1);
      user.setIsSuper(superUser ? 1 : 0);
      userMapper.insert(user);
    } else if (superUser && !user.isSuperUser()) {
      user.setIsSuper(1);
      userMapper.updateById(user);
      log.info("已校准超管账号 {}（is_super=1）", username);
    }
    ensureBinding(user.getId(), role.getId());
  }

  private void ensureBinding(Long userId, Long roleId) {
    Long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
        .eq(SysUserRole::getUserId, userId)
        .eq(SysUserRole::getRoleId, roleId));
    if (exists > 0) {
      return;
    }
    SysUserRole binding = new SysUserRole();
    binding.setUserId(userId);
    binding.setRoleId(roleId);
    userRoleMapper.insert(binding);
  }

  // ---------------- 系统管理菜单树（幂等） ----------------

  private void seedSystemMenus() {
    SysMenu system = seedCatalog(null, "System", "/system", "系统管理", "System",
        "lucide:settings", 1000);

    // 系统管理域不参与 RBAC 授权（由 /api/system/** 上的 @RequiresRoles("ADMIN") 一刀切），
    // 因此不播种 System:xxx 按钮权限码：这些 auth_code 永远不会参与判定，
    // 留在库里只会让人误以为按钮级权限在生效。BUTTON 型菜单能力本身保留，供业务域使用。
    seedMenu(system, "SystemUser", "user", "/system/user/index",
        "用户管理", "Users", "lucide:users", 1);
    seedMenu(system, "SystemRole", "role", "/system/role/index",
        "角色管理", "Roles", "lucide:shield-check", 2);
    seedMenu(system, "SystemMenu", "menu", "/system/menu/index",
        "菜单管理", "Menus", "lucide:list-tree", 3);

    // 审计日志（只读查询页）。放在 /system 下即自动归入 ADMIN 独占范围
    // （yorvix.menu.admin-only-paths 前缀匹配），因此不参与 RBAC 授权、也不需要按钮权限码。
    SysMenu log = seedCatalog(system, "SystemLog", "log", "日志管理", "Logs",
        "lucide:scroll-text", 4);
    seedMenu(log, "SystemLoginLog", "login", "/system/log/login/index",
        "登录日志", "Sign-in Logs", "lucide:log-in", 1);
    seedMenu(log, "SystemOperLog", "oper", "/system/log/oper/index",
        "操作日志", "Operation Logs", "lucide:clipboard-list", 2);
  }

  private SysMenu seedCatalog(SysMenu parent, String name, String path,
      String zh, String en, String icon, int sort) {
    return insertMenu(parent, SysMenu.TYPE_CATALOG, name, path, null, zh, en, icon, sort);
  }

  private SysMenu seedMenu(SysMenu parent, String name, String path, String component,
      String zh, String en, String icon, int sort) {
    return insertMenu(parent, SysMenu.TYPE_MENU, name, path, component, zh, en, icon, sort);
  }

  private SysMenu insertMenu(SysMenu parent, String type, String name, String path,
      String component, String zh, String en, String icon, int sort) {
    SysMenu exists = menuService.findByName(name);
    if (exists != null) {
      return exists;
    }
    SysMenu menu = new SysMenu();
    menu.setPid(parent == null ? 0L : parent.getId());
    menu.setType(type);
    menu.setName(name);
    menu.setPath(path);
    menu.setComponent(component);
    menu.setTitle(title(zh, en));
    menu.setIcon(icon);
    menu.setSort(sort);
    menu.setStatus(1);
    menu.setKeepAlive(0);
    menu.setAffixTab(0);
    return menuService.save(menu);
  }
}
