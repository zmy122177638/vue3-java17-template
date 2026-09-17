<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemUser } from '#/api/system/user';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal } from 'ant-design-vue';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { deleteUser, getUserPage, updateUserStatus } from '#/api/system/user';
import { $t } from '#/locales';

import { useColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import ResetPassword from './modules/reset-password.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ResetPasswordDrawer, resetPasswordDrawerApi] = useVbenDrawer({
  connectedComponent: ResetPassword,
  destroyOnClose: true,
});

function confirm(content: string, title: string) {
  return new Promise((resolve, reject) => {
    Modal.confirm({
      content,
      onCancel() {
        reject(new Error('已取消'));
      },
      onOk() {
        resolve(true);
      },
      title,
    });
  });
}

async function onStatusChange(newStatus: number, row: SystemUser) {
  const statusText: Record<number, string> = { 0: '禁用', 1: '启用' };
  try {
    await confirm(
      `你要将 ${row.username} 的状态切换为【${statusText[newStatus]}】吗？`,
      '切换状态',
    );
    await updateUserStatus(row.id, newStatus);
    return true;
  } catch {
    return false;
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useColumns(onStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        // sort 由 vxe 在 remote sort 模式下传入：{ field, order }
        query: async ({ page, sort }, formValues: Record<string, any>) => {
          return await getUserPage({
            keyword: formValues?.keyword,
            order: sort?.order,
            page: page.currentPage,
            size: page.pageSize,
            sort: sort?.field,
            status: formValues?.status,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    // remote：排序交给后端处理（后端按白名单校验）。不用本地排序，
    // 否则只排当前页、翻页后顺序又变，看起来像"排序不准"
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
  } as VxeTableGridOptions<SystemUser>,
});

function onEdit(row: SystemUser) {
  formDrawerApi.setData(row).open();
}

function onDelete(row: SystemUser) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.username]),
    duration: 0,
    key: 'action_process_msg',
  });
  deleteUser(row.id)
    .then(() => {
      message.success({
        content: $t('ui.actionMessage.deleteSuccess', [row.username]),
        key: 'action_process_msg',
      });
      onRefresh();
    })
    .catch(() => {
      hideLoading();
    });
}

/**
 * 重置密码改为抽屉表单。
 * <p>原实现用 window.prompt：明文输入无遮罩、默认值写死弱口令 123456、
 * 移动端及部分浏览器会直接屏蔽，且无法做长度/一致性校验。
 */
function onResetPassword(row: SystemUser) {
  resetPasswordDrawerApi.setData({ id: row.id, username: row.username }).open();
}

function onRefresh() {
  gridApi.query();
}

function onCreate() {
  formDrawerApi.setData(null).open();
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <ResetPasswordDrawer @success="onRefresh" />
    <Grid :table-title="$t('system.user.list')">
      <template #toolbar-tools>
        <Button
          class="!inline-flex items-center"
          type="primary"
          @click="onCreate"
        >
          <Plus class="size-5" />
          {{ $t('ui.actionTitle.create', [$t('system.user.title')]) }}
        </Button>
      </template>
      <template #action="{ row }">
        <VbenTableAction
          :actions="[
            {
              text: $t('common.edit'),
              icon: 'lucide:edit',
              disabled: row.editable === false,
              tooltip:
                row.editable === false
                  ? $t('system.user.notEditableTip')
                  : undefined,
              onClick: () => onEdit(row),
            },
          ]"
          :dropdown-actions="[
            {
              text: $t('system.user.resetPassword'),
              icon: 'lucide:key-round',
              disabled: row.editable === false,
              onClick: () => onResetPassword(row),
            },
            {
              text: $t('common.delete'),
              icon: 'lucide:trash-2',
              danger: true,
              disabled: row.editable === false,
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.username]),
                confirm: () => onDelete(row),
              },
            },
          ]"
          align="center"
        />
      </template>
    </Grid>
  </Page>
</template>
