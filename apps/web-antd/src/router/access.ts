import type { Router, RouteRecordRaw } from 'vue-router';

import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { useAccessStore, useTabbarStore, useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { getAllMenusApi } from '#/api';
import { BasicLayout, IFrameView } from '#/layouts';
import { $t } from '#/locales';
import { accessRoutes } from '#/router/routes';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

/**
 * 重新下发菜单并刷新侧边栏数据（与 `router/guard.ts` 首次生成菜单的逻辑一致）。
 * 菜单/按钮数据变更后调用，否则侧边栏仍是生成时的旧数据（如徽标不更新）。
 */
async function refreshAccessMenus(router: Router) {
  const accessStore = useAccessStore();
  const userStore = useUserStore();

  const { accessibleMenus, accessibleRoutes } = await generateAccess({
    roles: userStore.userInfo?.roles ?? [],
    router,
    routes: accessRoutes,
  });

  accessStore.setAccessMenus(accessibleMenus);
  accessStore.setAccessRoutes(accessibleRoutes);
  syncTabbarTitles(accessibleRoutes);
}

/** 按路由 name 深度查找最新标题 */
function findRouteTitle(
  routes: RouteRecordRaw[],
  name?: null | string,
): string | undefined {
  if (!name) {
    return undefined;
  }
  for (const route of routes) {
    if (route.name === name) {
      return typeof route.meta?.title === 'string'
        ? route.meta.title
        : undefined;
    }
    const title = findRouteTitle(
      (route.children ?? []) as RouteRecordRaw[],
      name,
    );
    if (title) {
      return title;
    }
  }
  return undefined;
}

/**
 * 已打开标签页的标题是打开时缓存的（后端菜单标题按语言下发），
 * 菜单重新下发后需要同步一次，否则切语言后标签栏仍是旧文案。
 */
function syncTabbarTitles(routes: RouteRecordRaw[]) {
  const tabbarStore = useTabbarStore();
  let changed = false;
  for (const tab of tabbarStore.getTabs) {
    // 页面自己设置过标题（newTabTitle）的不覆盖
    if (tab.meta?.newTabTitle) {
      continue;
    }
    const title = findRouteTitle(routes, tab.name as string);
    if (title && title !== tab.meta?.title) {
      tab.meta.title = title;
      changed = true;
    }
  }
  if (changed) {
    // 触发 useTabbar 内对 updateTime 的 watch，重新计算标签标题
    tabbarStore.setUpdateTime();
  }
}

/**
 * 剔除缺少路由地址（path）的节点。
 * vue-router 的 addRoute 要求 path 必须是字符串，`path: null` 会抛
 * `Cannot read properties of null (reading '0')`，导致整棵动态菜单注册失败。
 */
function sanitizeMenuRoutes(
  routes: RouteRecordStringComponent[],
): RouteRecordStringComponent[] {
  return routes
    .filter((route) => typeof route.path === 'string' && route.path.length > 0)
    .map((route) => ({
      ...route,
      children: route.children?.length
        ? sanitizeMenuRoutes(route.children)
        : undefined,
    }));
}

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      const hideLoading = message.loading({
        content: `${$t('common.loadingMenu')}...`,
        duration: 0,
        // 与 views/system/user 的写法一致：同 key 复用同一条提示，
        // 避免连续触发（如快速多次切换语言）时提示叠加成好几条
        key: 'menu_loading_msg',
      });
      try {
        return sanitizeMenuRoutes(await getAllMenusApi());
      } finally {
        // 成功、失败、超时都要关掉，否则提示会一直挂在页面上
        hideLoading();
      }
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

export { generateAccess, refreshAccessMenus };
