import type { Router } from 'vue-router';

import type { CurrentUserInfo } from '#/api';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';
import { resolveHomePath } from './home-path';

/**
 * 当前用户的首页地址：全局配置 → 菜单树第一个可落地页面 → null（由调用方回落默认首页）。
 * <p>菜单树里各节点的 path 已由 generateMenus 解析为最终绝对地址，可直接 router.push。
 */
function currentHomePath(): null | string {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const userInfo = userStore.userInfo as Partial<CurrentUserInfo> | undefined;
  return resolveHomePath(accessStore.accessMenus, userInfo?.homePath);
}

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        return decodeURIComponent(
          (to.query?.redirect as string) ||
            currentHomePath() ||
            preferences.app.defaultHomePath,
        );
      }
      // 权限正常的用户被 redirect（如浏览器历史里的 /auth/login?redirect=/no-permission…）
      // 带到“暂无权限”页时，弹回首页，避免误判成自己也没权限
      if (
        to.name === 'NoPermission' &&
        accessStore.accessToken &&
        accessStore.isAccessChecked
      ) {
        const userInfo = userStore.userInfo as
          | Partial<CurrentUserInfo>
          | undefined;
        if (userInfo && !userInfo.permissionIssue) {
          return currentHomePath() || preferences.app.defaultHomePath;
        }
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    const userInfo = userStore.userInfo || (await authStore.fetchUserInfo());
    const userRoles = userInfo.roles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    // 无权限时落地“暂无权限”提示页，并按后端下发的原因（未分配角色 / 角色被禁用 /
    // 角色无菜单授权）显示对应文案，避免用户按错误方向排查
    const permissionIssue = (userInfo as Partial<CurrentUserInfo>)
      .permissionIssue;
    if (permissionIssue) {
      return {
        name: 'NoPermission',
        query: { reason: permissionIssue },
        replace: true,
      };
    }

    // 首页由前端按**已生成的真实路由 + 菜单顺序**决定（accessMenus 此时已就绪）：
    // 全局配置优先，否则菜单树前序第一个可落地页面，都没有则用默认首页
    const homePath = currentHomePath();
    const redirectPath = (from.query.redirect ??
      (to.path === preferences.app.defaultHomePath
        ? homePath || preferences.app.defaultHomePath
        : to.fullPath)) as string;

    return {
      ...router.resolve(decodeURIComponent(redirectPath)),
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
