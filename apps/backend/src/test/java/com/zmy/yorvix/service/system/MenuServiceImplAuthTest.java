package com.zmy.yorvix.service.system;

import com.zmy.yorvix.common.exception.BizException;
import com.zmy.yorvix.config.MenuProperties;
import com.zmy.yorvix.model.system.SysMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 菜单授权规范化逻辑（{@link MenuServiceImpl#resolveAuthorizedMenuIds} 与
 * {@link MenuServiceImpl#adminOnlyMenuIds}）。
 * <p>这两段是"错了也不报错"的典型：授权少了或多了、子菜单被当成顶级路由（前端注册到错误位置）、
 * ADMIN 独占菜单被写进授权表，都不会抛异常，只会让行为悄悄不对，因此用单测锁住。
 * <p>被测方法不触碰数据库，因此构造 Service 时 mapper / objectMapper 传 null 即可。
 */
class MenuServiceImplAuthTest {

  private static final long SYSTEM_ID = 1L;
  private static final long SYSTEM_USER_ID = 2L;
  private static final long SYSTEM_USER_BUTTON_ID = 3L;
  private static final long SYSTEMATIC_ID = 10L;
  private static final long TEST_ID = 20L;
  private static final long TEST_CHILD_ID = 21L;
  private static final long TEST_GRANDCHILD_ID = 22L;

  private static MenuServiceImpl newService() {
    MenuProperties properties = new MenuProperties();
    properties.setAdminOnlyPaths(List.of("/system"));
    return new MenuServiceImpl(null, null, null, properties, null);
  }

  private static List<SysMenu> menuTree() {
    List<SysMenu> menus = new ArrayList<>();
    menus.add(menu(SYSTEM_ID, 0L, SysMenu.TYPE_CATALOG, "/system"));
    menus.add(menu(SYSTEM_USER_ID, SYSTEM_ID, SysMenu.TYPE_MENU, "user"));
    menus.add(menu(SYSTEM_USER_BUTTON_ID, SYSTEM_USER_ID, SysMenu.TYPE_BUTTON, null));
    // 前缀与 /system 相近，但按路径段边界匹配不该被判定为独占
    menus.add(menu(SYSTEMATIC_ID, 0L, SysMenu.TYPE_CATALOG, "/systematic"));
    menus.add(menu(TEST_ID, 0L, SysMenu.TYPE_CATALOG, "/test"));
    menus.add(menu(TEST_CHILD_ID, TEST_ID, SysMenu.TYPE_CATALOG, "/test/child"));
    menus.add(menu(TEST_GRANDCHILD_ID, TEST_CHILD_ID, SysMenu.TYPE_BUTTON, null));
    return menus;
  }

  private static SysMenu menu(long id, Long pid, String type, String path) {
    SysMenu menu = new SysMenu();
    menu.setId(id);
    menu.setPid(pid);
    menu.setType(type);
    menu.setPath(path);
    menu.setName("menu-" + id);
    return menu;
  }

  // ---------------- ADMIN 独占范围 ----------------

  @Test
  @DisplayName("ADMIN 独占范围含全部后代，但不误伤前缀相近的 /systematic")
  void adminOnlyCoversSubtreeWithoutPrefixFalsePositive() {
    Set<Long> adminOnlyIds = newService().adminOnlyMenuIds(menuTree());

    assertThat(adminOnlyIds)
        .containsExactlyInAnyOrder(SYSTEM_ID, SYSTEM_USER_ID, SYSTEM_USER_BUTTON_ID);
  }

  @Test
  @DisplayName("admin-only-paths 为空时不判定任何菜单为独占")
  void noAdminOnlyWhenNotConfigured() {
    MenuProperties properties = new MenuProperties();
    properties.setAdminOnlyPaths(List.of());

    Set<Long> adminOnlyIds = new MenuServiceImpl(null, null, null, properties, null)
        .adminOnlyMenuIds(menuTree());

    assertThat(adminOnlyIds).isEmpty();
  }

  // ---------------- 授权规范化 ----------------

  @Test
  @DisplayName("只提交孙节点时自动补全所有祖先，避免下发时子菜单被当成顶级路由")
  void completesAncestors() {
    Set<Long> resolved = newService()
        .resolveAuthorizedMenuIds(menuTree(), List.of(TEST_GRANDCHILD_ID));

    assertThat(resolved).containsExactlyInAnyOrder(TEST_ID, TEST_CHILD_ID, TEST_GRANDCHILD_ID);
  }

  @Test
  @DisplayName("重复 id 被去重（否则会撞 uk_role_menu 唯一键导致 500）")
  void deduplicatesSubmittedIds() {
    Set<Long> resolved = newService()
        .resolveAuthorizedMenuIds(menuTree(), List.of(TEST_CHILD_ID, TEST_CHILD_ID, TEST_ID));

    assertThat(resolved).containsExactlyInAnyOrder(TEST_ID, TEST_CHILD_ID);
  }

  @Test
  @DisplayName("ADMIN 独占菜单即使被显式提交也会连同其祖先一起剔除")
  void dropsAdminOnlyMenus() {
    Set<Long> resolved = newService()
        .resolveAuthorizedMenuIds(menuTree(), List.of(TEST_ID, SYSTEM_USER_ID));

    assertThat(resolved).containsExactly(TEST_ID);
  }

  @Test
  @DisplayName("全部授权都落在独占范围内时结果为空（调用方据此清空授权）")
  void returnsEmptyWhenEverythingIsAdminOnly() {
    Set<Long> resolved = newService()
        .resolveAuthorizedMenuIds(menuTree(), List.of(SYSTEM_USER_BUTTON_ID));

    assertThat(resolved).isEmpty();
  }

  @Test
  @DisplayName("无效菜单 ID 直接报错，避免写入脏授权")
  void rejectsUnknownMenuId() {
    List<SysMenu> menus = menuTree();

    // 断言 i18n key 而不是最终文案：文案随 Accept-Language 变化，key 才是稳定契约
    // （BizException 携带 key，由 GlobalExceptionHandler 按请求语言解析）
    assertThatThrownBy(() -> newService().resolveAuthorizedMenuIds(menus, List.of(9999L)))
        .isInstanceOf(BizException.class)
        .hasMessage("error.menu.invalid");
  }

  @Test
  @DisplayName("空输入返回空集合")
  void handlesEmptyInput() {
    assertThat(newService().resolveAuthorizedMenuIds(menuTree(), List.of())).isEmpty();
    assertThat(newService().resolveAuthorizedMenuIds(menuTree(), null)).isEmpty();
  }

  @Test
  @DisplayName("脏数据成环时不会死循环")
  void survivesCyclicData() {
    List<SysMenu> cyclic = new ArrayList<>(menuTree());
    // 让 /test 的父级指向它自己的孙节点，构造 20 -> 21 -> 22 -> 20 的环
    cyclic.set(4, menu(TEST_ID, TEST_GRANDCHILD_ID, SysMenu.TYPE_CATALOG, "/test"));

    Set<Long> resolved = newService().resolveAuthorizedMenuIds(cyclic, List.of(TEST_CHILD_ID));

    assertThat(resolved).containsExactlyInAnyOrder(TEST_ID, TEST_CHILD_ID, TEST_GRANDCHILD_ID);
  }
}
