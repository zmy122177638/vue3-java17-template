<script lang="ts" setup>
import type { Recordable } from '@vben/types';

import type { MenuNode, MenuType } from '#/api/system/menu';
import type { RoleUpsertParams, SystemRole } from '#/api/system/role';

import { computed, ref } from 'vue';

import { Tree, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { message, Spin } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getMenuTree, resolveMenuTitle } from '#/api/system/menu';
import { createRole, getRoleMenuIds, updateRole } from '#/api/system/role';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

/** 授权树节点：只保留 Tree 渲染与提交需要的字段 */
interface AuthTreeNode {
  children: AuthTreeNode[];
  icon?: null | string;
  id: number;
  name: string;
  type: MenuType;
}

const emits = defineEmits(['success']);

const id = ref<number>();

const menuTree = ref<AuthTreeNode[]>([]);
const checkedIds = ref<number[]>([]);
const loadingMenus = ref(false);

const [Form, formApi] = useVbenForm({
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer<null | SystemRole>({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = (await formApi.getValues()) as Recordable<any>;
    // 只提交 RoleUpsertRequest 声明的字段：整行回填会把 id / createdAt 混进请求体，
    // 那只是靠 Spring Boot 默认关闭 FAIL_ON_UNKNOWN_PROPERTIES 才没报错（隐式依赖）
    const payload: RoleUpsertParams = {
      code: values.code,
      menuIds: checkedIds.value,
      name: values.name,
      remark: values.remark,
      status: values.status,
    };
    drawerApi.lock();
    const request = id.value
      ? updateRole(id.value, payload)
      : createRole(payload);
    request
      .then(() => {
        message.success($t('ui.actionMessage.operationSuccess'));
        emits('success');
        drawerApi.close();
      })
      .catch(() => {
        drawerApi.unlock();
      });
  },

  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData();
    formApi.reset();
    id.value = data?.id;
    checkedIds.value = [];
    // 编辑时把角色数据回填表单（编码会因此被依赖规则置为只读）
    if (data?.id) {
      await formApi.setValues(data as Recordable<any>);
    }

    loadingMenus.value = true;
    try {
      const [tree, ids] = await Promise.all([
        getMenuTree(),
        data?.id ? getRoleMenuIds(data.id) : Promise.resolve([]),
      ]);
      menuTree.value = toTreeData(tree);
      // 历史数据里若勾过被剔除的菜单（如系统管理），一并过滤，保存时自动清理
      const available = collectIds(menuTree.value);
      checkedIds.value = ids.filter((id) => available.has(id));
    } finally {
      loadingMenus.value = false;
    }
  },
});

defineExpose({ drawerApi });

/**
 * 转换授权树数据。
 * <p>ADMIN 独占菜单由后端按 `yorvix.menu.admin-only-paths` 标记为 `adminOnly`，这里直接过滤：
 * 它们对应的接口都标了 @RequiresRoles("ADMIN")，自定义角色勾选无效（被分配者能看到菜单，
 * 但一访问接口就 403），展示出来只会造成“勾了没用”的困惑。
 * <p>用后端标记而不是前端硬编码路径，是为了让「接口鉴权」与「授权树可见性」共用同一配置源，
 * 以后新增 ADMIN 独占菜单时不会漏改前端。
 */
function toTreeData(nodes: MenuNode[]): AuthTreeNode[] {
  return nodes
    .filter((node) => node.adminOnly !== true)
    .map((node) => ({
      children: toTreeData(node.children ?? []),
      icon: node.icon,
      id: node.id,
      // 直接解析原始 title，不依赖 MenuTreeNode 的 displayTitle：
      // MenuNode.children 的类型是 MenuNode[]，用 displayTitle 会让递归无法通过类型检查
      name: resolveMenuTitle(node.title),
      type: node.type,
    }));
}

function collectIds(nodes: AuthTreeNode[]): Set<number> {
  const ids = new Set<number>();
  const walk = (list: AuthTreeNode[]) => {
    for (const node of list) {
      ids.add(node.id);
      if (node.children.length > 0) {
        walk(node.children);
      }
    }
  };
  walk(nodes);
  return ids;
}

function getNodeClass(node: Record<string, any>) {
  return node.value?.type === 'BUTTON' ? 'inline-flex' : '';
}

const getDrawerTitle = computed(() =>
  id.value
    ? $t('ui.actionTitle.edit', [$t('system.role.title')])
    : $t('ui.actionTitle.create', [$t('system.role.title')]),
);
</script>
<template>
  <Drawer class="w-full max-w-140" :title="getDrawerTitle">
    <Spin :spinning="loadingMenus" wrapper-class-name="w-full">
      <Form />
      <div class="border-border mt-2 rounded-md border p-3">
        <div class="text-foreground mb-2 text-sm font-medium">
          {{ $t('system.role.menus') }}
        </div>
        <div class="text-muted-foreground mb-2 text-xs">
          {{ $t('system.role.adminOnlyTip') }}
        </div>
        <!-- Tree 的选中值是标准 v-model（modelValue），写成 v-model:checked-ids 绑不上去，
             会导致勾选不进 checkedIds、保存时 menuIds 恒为空 -->
        <Tree
          v-model="checkedIds"
          :tree-data="menuTree"
          :get-node-class="getNodeClass"
          bordered
          :default-expanded-level="2"
          multiple
          value-field="id"
          label-field="name"
        >
          <template #node="{ value }">
            <IconifyIcon
              v-if="value.type === 'BUTTON'"
              icon="carbon:security"
              class="size-4"
            />
            <IconifyIcon
              v-else-if="value.icon"
              :icon="value.icon"
              class="size-4"
            />
            {{ value.name }}
          </template>
        </Tree>
      </div>
    </Spin>
  </Drawer>
</template>
