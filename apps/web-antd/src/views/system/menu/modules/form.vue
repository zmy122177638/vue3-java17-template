<script lang="ts" setup>
import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';
import type { MenuNode, MenuUpsertParams } from '#/api/system/menu';

import { computed, h, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { getPopupContainer } from '@vben/utils';

import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';

import { useVbenForm, z } from '#/adapter/form';
import {
  BADGE_VARIANTS,
  createMenu,
  getMenuTree,
  updateMenu,
} from '#/api/system/menu';
import { $t } from '#/locales';
import { componentKeys } from '#/router/routes';

import { getMenuTypeOptions } from '../data';

const emit = defineEmits<{
  success: [];
}>();

/** 抽屉数据：编辑时为完整节点，新增/新增下级时为 { pid } */
type MenuDrawerData = MenuNode | { pid?: number };

const formData = ref<MenuNode>();

const HAS_ICON = new Set(['CATALOG', 'EMBEDDED', 'LINK', 'MENU']);
const HAS_ROUTE = new Set(['CATALOG', 'EMBEDDED', 'MENU']);
/** 需要前端路由地址的菜单类型（外链由 meta.link 直接打开外部地址，无需路由地址） */
const HAS_PATH = new Set(['CATALOG', 'EMBEDDED', 'MENU']);

const schema: VbenFormSchema<MenuUpsertParams>[] = [
  {
    component: 'RadioGroup',
    componentProps: {
      buttonStyle: 'solid',
      options: getMenuTypeOptions(),
      optionType: 'button',
    },
    defaultValue: 'MENU',
    fieldName: 'type',
    formItemClass: 'col-span-2 md:col-span-2',
    label: $t('system.menu.type'),
  },
  {
    component: 'Input',
    componentProps: {
      // 与后端 @Size(max = 64) 对齐
      maxlength: 64,
    },
    fieldName: 'name',
    label: $t('system.menu.name'),
    // 必填字段必须用内置 'required' 规则：
    // vben 对「必填」字段会剥掉 zod 的外层包装（optional/refine），
    // 用 zod 校验 undefined 会直接报 “expected string, received undefined”
    rules: 'required',
  },
  {
    component: 'ApiTreeSelect',
    componentProps: {
      allowClear: true,
      api: getMenuTree,
      childrenField: 'children',
      class: 'w-full',
      filterTreeNode(input: string, node: Recordable<any>) {
        if (!input || input.length === 0) {
          return true;
        }
        return (node.displayTitle ?? '').includes(input);
      },
      getPopupContainer,
      labelField: 'displayTitle',
      showSearch: true,
      treeDefaultExpandAll: true,
      valueField: 'id',
    },
    fieldName: 'pid',
    label: $t('system.menu.parent'),
    renderComponentContent() {
      return {
        title({ label, icon }: Recordable<any>) {
          const coms = [];
          if (icon) {
            coms.push(h(IconifyIcon, { class: 'size-4', icon }));
          }
          coms.push(h('span', { class: '' }, label ?? ''));
          return h('div', { class: 'flex items-center gap-1' }, coms);
        },
      };
    },
  },
  {
    component: 'I18nInput',
    fieldName: 'title',
    formItemClass: 'col-span-2 md:col-span-2',
    label: $t('system.menu.displayTitle'),
    rules: 'required',
  },
  {
    component: 'Input',
    componentProps: {
      maxlength: 100,
    },
    dependencies: {
      // 路由地址是前端注册路由的必需字段（缺了会让菜单注册失败）
      rules: (values) => (HAS_PATH.has(values.type ?? '') ? 'required' : null),
      show: (values) => HAS_PATH.has(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'path',
    help: $t('system.menu.pathHelp'),
    label: $t('system.menu.path'),
  },
  {
    component: 'Input',
    dependencies: {
      show: (values) => ['EMBEDDED', 'MENU'].includes(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'activePath',
    help: $t('system.menu.activePathHelp'),
    label: $t('system.menu.activePath'),
    rules: z
      .string()
      .max(
        100,
        $t('ui.formRules.maxLength', [$t('system.menu.activePath'), 100]),
      )
      // 非必填：后端未填写时下发 null，需允许空值
      .nullable()
      .optional(),
  },
  {
    component: 'IconPicker',
    componentProps: {
      // 从 Iconify 拉取完整 lucide 图标集（与种子数据 / 项目图标风格一致）
      prefix: 'lucide',
    },
    dependencies: {
      show: (values) => HAS_ICON.has(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'icon',
    label: $t('system.menu.icon'),
  },
  {
    component: 'IconPicker',
    componentProps: {
      // 从 Iconify 拉取完整 lucide 图标集（与种子数据 / 项目图标风格一致）
      prefix: 'lucide',
    },
    dependencies: {
      show: (values) => HAS_ROUTE.has(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'activeIcon',
    label: $t('system.menu.activeIcon'),
  },
  {
    component: 'AutoComplete',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      filterOption(input: string, option: { value: string }) {
        return option.value.toLowerCase().includes(input.toLowerCase());
      },
      options: componentKeys.map((v) => ({ value: v })),
    },
    dependencies: {
      rules: (values) => (values.type === 'MENU' ? 'required' : null),
      show: (values) => values.type === 'MENU',
      triggerFields: ['type'],
    },
    fieldName: 'component',
    label: $t('system.menu.component'),
  },
  {
    component: 'Input',
    dependencies: {
      rules: (values) =>
        ['EMBEDDED', 'LINK'].includes(values.type ?? '') ? 'required' : null,
      show: (values) => ['EMBEDDED', 'LINK'].includes(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'linkSrc',
    label: $t('system.menu.linkSrc'),
  },
  {
    component: 'Input',
    dependencies: {
      rules: (values) => (values.type === 'BUTTON' ? 'required' : null),
      // 外链不需要权限标识
      show: (values) => values.type !== 'LINK',
      triggerFields: ['type'],
    },
    fieldName: 'authCode',
    label: $t('system.menu.authCode'),
  },
  {
    component: 'Select',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      options: [
        { label: $t('system.menu.badgeType.dot'), value: 'dot' },
        { label: $t('system.menu.badgeType.normal'), value: 'normal' },
      ],
    },
    dependencies: {
      show: (values) => values.type !== 'BUTTON',
      triggerFields: ['type'],
    },
    fieldName: 'badgeType',
    label: $t('system.menu.badgeType.title'),
  },
  {
    component: 'Input',
    dependencies: {
      resolve: ({ values }) => {
        return {
          componentProps: {
            allowClear: true,
            disabled: values.badgeType !== 'normal',
          },
          show: values.type !== 'BUTTON',
        };
      },
      triggerFields: ['badgeType', 'type'],
    },
    fieldName: 'badge',
    label: $t('system.menu.badge'),
  },
  {
    component: 'Select',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      options: BADGE_VARIANTS.map((v) => ({ label: v, value: v })),
    },
    dependencies: {
      show: (values) => values.type !== 'BUTTON',
      triggerFields: ['type'],
    },
    fieldName: 'badgeVariants',
    label: $t('system.menu.badgeVariants'),
  },
  {
    component: 'RadioGroup',
    componentProps: {
      buttonStyle: 'solid',
      options: [
        { label: $t('common.enabled'), value: 1 },
        { label: $t('common.disabled'), value: 0 },
      ],
      optionType: 'button',
    },
    defaultValue: 1,
    fieldName: 'status',
    label: $t('system.menu.status'),
  },
  {
    component: 'Divider',
    dependencies: {
      show: (values) => HAS_ROUTE.has(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'divider1',
    formItemClass: 'col-span-2 md:col-span-2 pb-0',
    hideLabel: true,
    renderComponentContent() {
      return {
        default: () => $t('system.menu.advancedSettings'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => values.type === 'MENU',
      triggerFields: ['type'],
    },
    fieldName: 'keepAlive',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.keepAlive'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => ['EMBEDDED', 'MENU'].includes(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'affixTab',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.affixTab'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => values.type !== 'BUTTON',
      triggerFields: ['type'],
    },
    fieldName: 'hideInMenu',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideInMenu'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => ['CATALOG', 'MENU'].includes(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'hideChildrenInMenu',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideChildrenInMenu'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => HAS_ROUTE.has(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'hideInBreadcrumb',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideInBreadcrumb'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => HAS_ROUTE.has(values.type ?? ''),
      triggerFields: ['type'],
    },
    fieldName: 'hideInTab',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideInTab'),
      };
    },
  },
];

const breakpoints = useBreakpoints(breakpointsTailwind);
const isHorizontal = computed(() => breakpoints.greaterOrEqual('md').value);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    colon: true,
    formItemClass: 'col-span-2 md:col-span-1',
  },
  schema,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2 gap-x-4',
});

const [Drawer, drawerApi] = useVbenDrawer<MenuDrawerData>({
  onConfirm: onSubmit,
  async onOpenChange(isOpen) {
    if (!isOpen) {
      return;
    }
    const data = drawerApi.getData();
    if (data && 'id' in data) {
      formData.value = data;
      await formApi.setValues(data as Recordable<any>);
    } else {
      formData.value = undefined;
      formApi.reset();
      await formApi.setValues({
        pid: data?.pid ?? 0,
      } as Recordable<any>);
    }
  },
});

defineExpose({ drawerApi });

async function onSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  drawerApi.lock();
  const values = (await formApi.getValues()) as Recordable<any>;
  // 剔除树节点自有字段，只提交上行 DTO 需要的部分。
  // displayTitle/rawTitle 由 getMenuTree 的 decorate 附加在行数据上，回填后会随表单一起提交，
  // 同样只能靠 Spring Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES 兜住，因此这里显式剔除
  const { children, displayTitle, id, rawTitle, ...rest } = values;
  void children;
  void displayTitle;
  void id;
  void rawTitle;
  const payload = {
    ...rest,
    pid: values.pid ?? 0,
  } as MenuUpsertParams;
  if (payload.type === 'LINK') {
    // 外链不需要路由地址与权限标识，避免切换类型后把残留值提交上去
    delete payload.authCode;
    delete payload.path;
  }
  try {
    await (formData.value?.id
      ? updateMenu(formData.value.id, payload)
      : createMenu(payload));
    drawerApi.close();
    emit('success');
  } finally {
    drawerApi.unlock();
  }
}

const getDrawerTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', [$t('system.menu.title')])
    : $t('ui.actionTitle.create', [$t('system.menu.title')]),
);
</script>
<template>
  <Drawer class="w-full max-w-200" :title="getDrawerTitle">
    <Form class="mx-4" :layout="isHorizontal ? 'horizontal' : 'vertical'" />
  </Drawer>
</template>
