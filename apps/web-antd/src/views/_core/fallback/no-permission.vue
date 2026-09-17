<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Button } from 'ant-design-vue';

import { $t } from '#/locales';
import { useAuthStore } from '#/store';

defineOptions({ name: 'NoPermission' });

const REASONS = ['NO_ROLE', 'ROLE_DISABLED', 'NO_MENU'] as const;

const authStore = useAuthStore();
const route = useRoute();

// 原因由 router/guard.ts 跳转时带上（后端 /api/user/info 的 permissionIssue）
const reason = computed(() => {
  const fromQuery = String(route.query.reason ?? '');
  return (REASONS as readonly string[]).includes(fromQuery)
    ? fromQuery
    : 'NO_ROLE';
});

async function onRelogin() {
  // 退出登录后回到登录页。
  // 这里显式不带 redirect：否则下一个登录的账号会被直接送回本页
  await authStore.logout(false);
}
</script>

<template>
  <div
    class="bg-background flex h-screen flex-col items-center justify-center gap-4 p-10 text-center"
  >
    <IconifyIcon
      class="text-muted-foreground size-16"
      icon="lucide:shield-off"
    />
    <h2 class="text-lg font-medium">{{ $t('page.noPermission.title') }}</h2>
    <p class="text-muted-foreground max-w-md text-sm">
      {{ $t(`page.noPermission.desc.${reason}`) }}
    </p>
    <Button type="primary" @click="onRelogin">
      {{ $t('page.noPermission.relogin') }}
    </Button>
  </div>
</template>
