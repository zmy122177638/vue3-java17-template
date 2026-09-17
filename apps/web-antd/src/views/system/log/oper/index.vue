<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { OperLogItem } from '#/api/system/log';

import { Page } from '@vben/common-ui';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getOperLogPage } from '#/api/system/log';
import { $t } from '#/locales';

import { useColumns, useGridFormSchema } from './data';

/**
 * 操作日志：只读审计视图。
 * <p>记录口径（哪些请求会留痕）由后端 `security/OperationLogInterceptor` 定义：
 * `/api/system/**` 下的非 GET 请求，成功与失败都记。
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
          return await getOperLogPage({
            module: formValues?.module,
            order: sort?.order,
            page: page.currentPage,
            size: page.pageSize,
            sort: sort?.field,
            success: formValues?.success,
            username: formValues?.username,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
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
  } as VxeTableGridOptions<OperLogItem>,
});
</script>
<template>
  <Page auto-content-height>
    <Grid :table-title="$t('system.log.operTitle')" />
  </Page>
</template>
