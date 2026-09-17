import { preferences } from '@vben/preferences';

import { requestClient } from '#/api/request';

/** 徽标颜色集合（对应 @vben-core/menu-ui 的 MenuBadge variants） */
export const BADGE_VARIANTS = [
  'default',
  'destructive',
  'primary',
  'success',
  'warning',
] as const;

/** 徽标类型集合 */
export const BADGE_TYPES = ['dot', 'normal'] as const;

/** 菜单类型集合（与后端 SysMenu 类型常量一致） */
export const MENU_TYPES = [
  'CATALOG',
  'MENU',
  'BUTTON',
  'EMBEDDED',
  'LINK',
] as const;

/** 菜单类型 */
export type MenuType = (typeof MENU_TYPES)[number];

/** 多语言 title JSON -> 当前语言显示文本 */
export function resolveMenuTitle(titleJson: string): string {
  if (!titleJson) return '';
  try {
    const map = JSON.parse(titleJson) as Record<string, string>;
    const lang = preferences.app.locale;
    return map[lang] ?? map['zh-CN'] ?? Object.values(map)[0] ?? '';
  } catch {
    return titleJson;
  }
}

/** 管理端菜单树节点（后端 MenuNode） */
export interface MenuNode {
  affixTab: boolean;
  /**
   * 是否 ADMIN 独占（后端按 `yorvix.menu.admin-only-paths` 标记，含后代）。
   * 这类菜单受接口层 `@RequiresRoles("ADMIN")` 保护、不参与 RBAC 授权，
   * 角色授权树据此隐藏；菜单管理页仍需展示它们，因此后端只给标记、不直接过滤。
   */
  adminOnly: boolean;
  /** 激活时图标 */
  activeIcon?: null | string;
  /** 作为路由时需要高亮的菜单路径 */
  activePath?: null | string;
  authCode?: null | string;
  badge?: null | string;
  badgeType?: null | string;
  badgeVariants?: null | string;
  children: MenuNode[];
  component?: null | string;
  hideChildrenInMenu?: boolean;
  hideInBreadcrumb?: boolean;
  hideInMenu?: boolean;
  hideInTab?: boolean;
  icon?: null | string;
  id: number;
  keepAlive: boolean;
  /** EMBEDDED 内嵌地址 / LINK 外链地址 */
  linkSrc?: null | string;
  name?: null | string;
  path?: null | string;
  pid: number;
  sort: number;
  status: number;
  title: string;
  type: MenuType;
}

export interface MenuUpsertParams {
  affixTab?: boolean;
  activeIcon?: string;
  activePath?: string;
  authCode?: string;
  badge?: string;
  badgeType?: string;
  badgeVariants?: string;
  component?: string;
  hideChildrenInMenu?: boolean;
  hideInBreadcrumb?: boolean;
  hideInMenu?: boolean;
  hideInTab?: boolean;
  icon?: string;
  keepAlive?: boolean;
  linkSrc?: string;
  name?: string;
  path?: string;
  pid?: number;
  sort?: number;
  status?: number;
  title: string;
  type: MenuType;
}

/** 菜单树节点：title 解析为当前语言 displayTitle，rawTitle 保留原始 JSON 供编辑 */
export type MenuTreeNode = MenuNode & {
  displayTitle: string;
  rawTitle: string;
};

/** 全量菜单树（含按钮） */
export async function getMenuTree(): Promise<MenuTreeNode[]> {
  const tree = await requestClient.get<MenuNode[]>('/system/menus/tree');
  const decorate = (nodes: MenuNode[]): MenuTreeNode[] =>
    nodes.map((node) => ({
      ...node,
      displayTitle: resolveMenuTitle(node.title),
      rawTitle: node.title,
      children: decorate(node.children ?? []),
    }));
  return decorate(tree);
}

/** 同级拖拽排序参数 */
export interface MenuSortParams {
  /** 同一父级下菜单 ID 的新顺序 */
  ids: number[];
  /** 父级 ID，0 为根 */
  pid: number;
}

/** 同级拖拽排序（按 ids 顺序重排 pid 下的兄弟节点） */
export async function sortMenus(params: MenuSortParams) {
  return requestClient.put<boolean>('/system/menus/sort', params);
}

export async function createMenu(params: MenuUpsertParams) {
  return requestClient.post<boolean>('/system/menus', params);
}

export async function updateMenu(id: number, params: MenuUpsertParams) {
  return requestClient.put<boolean>(`/system/menus/${id}`, params);
}

export async function deleteMenu(id: number) {
  return requestClient.delete<boolean>(`/system/menus/${id}`);
}
