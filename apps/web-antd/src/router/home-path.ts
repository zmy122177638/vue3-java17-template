/**
 * 登录后首页地址的计算（放在前端，因为只有这里同时掌握"真实路由表"与"菜单顺序"）。
 *
 * 取值优先级：
 * 1. 后端全局配置 `yorvix.menu.home-path`（所有用户一致，可为空）；
 * 2. 菜单树前序遍历中第一个可落地页面（菜单里的 path 已由 generateMenus 从
 *    router.getRoutes() 解析为**最终绝对地址**，因此这里不需要再做父级拼接）；
 * 3. 都没有则返回 null，由调用方回落到 `preferences.app.defaultHomePath`。
 */
interface MenuLike {
  children?: MenuLike[];
  path?: string;
}

/**
 * 外链菜单的 path 是外链地址本身（generateMenus 里取 `meta.link || path`），
 * 不能交给 router.push，否则会被当成站内相对地址。
 */
function isExternalLink(path?: string): boolean {
  return /^https?:\/\//i.test(path ?? '');
}

/** 前序遍历找第一个可作为落地页的地址（跳过外链节点） */
function findFirstPagePath(menus: MenuLike[]): null | string {
  for (const menu of menus) {
    if (menu.children?.length) {
      const childPath = findFirstPagePath(menu.children);
      if (childPath) {
        return childPath;
      }
      continue;
    }
    if (menu.path && !isExternalLink(menu.path)) {
      return menu.path;
    }
  }
  return null;
}

/**
 * 计算登录后首页地址。
 *
 * @param menus 已生成的菜单树（accessStore.accessMenus）
 * @param configuredHomePath 后端下发的全局首页配置（/api/user/info 的 homePath）
 * @returns 首页地址；返回 null 表示无可用页面，由调用方回落默认首页
 */
export function resolveHomePath(
  menus: MenuLike[],
  configuredHomePath?: null | string,
): null | string {
  if (configuredHomePath) {
    return configuredHomePath;
  }
  return findFirstPagePath(menus);
}
