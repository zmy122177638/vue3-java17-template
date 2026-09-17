package com.zmy.yorvix.response.system;

import com.zmy.yorvix.model.system.SysMenu;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端菜单树节点（含按钮型，title 为原始多语言 JSON，由前端编辑表单拆分为双语文本）。
 */
@Getter
@Setter
public class MenuNode {

  private Long id;
  private Long pid;
  /** CATALOG / MENU / BUTTON / EMBEDDED / LINK */
  private String type;
  private String name;
  private String path;
  private String component;
  private String authCode;
  /** 原始多语言 JSON */
  private String title;
  private String icon;
  private String activeIcon;
  private String activePath;
  /** EMBEDDED 内嵌地址 / LINK 外链地址 */
  private String linkSrc;
  private String badgeType;
  private String badge;
  private String badgeVariants;
  private Boolean hideInMenu;
  private Boolean hideChildrenInMenu;
  private Boolean hideInBreadcrumb;
  private Boolean hideInTab;
  private Integer sort;
  private Integer status;
  private Boolean keepAlive;
  private Boolean affixTab;
  /**
   * 是否 ADMIN 独占（自身或其祖先命中 {@code yorvix.menu.admin-only-paths}）。
   * <p>这类菜单受 {@code @RequiresRoles("ADMIN")} 保护、不参与 RBAC 授权，
   * 因此不应出现在角色授权树中（菜单管理页仍需展示，故这里只给标记、不直接过滤）。
   */
  private Boolean adminOnly = false;
  private List<MenuNode> children = new ArrayList<>();

  public static MenuNode of(SysMenu menu) {
    MenuNode node = new MenuNode();
    node.setId(menu.getId());
    node.setPid(menu.getPid());
    node.setType(menu.getType());
    node.setName(menu.getName());
    node.setPath(menu.getPath());
    node.setComponent(menu.getComponent());
    node.setAuthCode(menu.getAuthCode());
    node.setTitle(menu.getTitle());
    node.setIcon(menu.getIcon());
    node.setActiveIcon(menu.getActiveIcon());
    node.setActivePath(menu.getActivePath());
    node.setLinkSrc(menu.getLinkSrc());
    node.setBadgeType(menu.getBadgeType());
    node.setBadge(menu.getBadge());
    node.setBadgeVariants(menu.getBadgeVariants());
    node.setHideInMenu(menu.getHideInMenu() != null && menu.getHideInMenu() == 1);
    node.setHideChildrenInMenu(menu.getHideChildrenInMenu() != null && menu.getHideChildrenInMenu() == 1);
    node.setHideInBreadcrumb(menu.getHideInBreadcrumb() != null && menu.getHideInBreadcrumb() == 1);
    node.setHideInTab(menu.getHideInTab() != null && menu.getHideInTab() == 1);
    node.setSort(menu.getSort());
    node.setStatus(menu.getStatus());
    node.setKeepAlive(menu.getKeepAlive() != null && menu.getKeepAlive() == 1);
    node.setAffixTab(menu.getAffixTab() != null && menu.getAffixTab() == 1);
    return node;
  }
}
