<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoginLogItem } from '#/api/system/log';

import { Page } from '@vben/common-ui';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getLoginLogPage } from '#/api/system/log';
import { $t } from '#/locales';

import { useColumns, useGridFormSchema } from './data';

/**
 * 登录日志：只读审计视图。
 * <p>没有新增/编辑/删除入口——审计数据一旦可以被界面改写，就不再是证据。
 */
const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page, sort }, formValues: Record<string, any>) => {
          return await getLoginLogPage({
            event: formValues?.event,
            order: sort?.order,
            page: page.currentPage,
            size: page.pageSize,
            sort: sort?.field,
            username: formValues?.username,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    // remote：排序交给后端（后端按白名单校验），避免"只排当前页"的假排序
    sortConfig: {
      remote: true,
    },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<LoginLogItem>,
});
</script>
<template>
  <Page auto-content-height>
    <Grid :table-title="$t('system.log.loginTitle')">
      <!-- 补充信息：展示已本地化的文案，悬停可看到原始 i18n key -->
      <template #detail="{ row }">
        <span :title="row.detailKey || ''">{{ row.detail || '—' }}</span>
      </template>
    </Grid>
  </Page>
</template>
