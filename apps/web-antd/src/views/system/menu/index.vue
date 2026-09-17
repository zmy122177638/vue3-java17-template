<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MenuNode, MenuTreeNode } from '#/api/system/menu';

import { nextTick, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { useSortable } from '@vben/hooks';
import { IconifyIcon, Plus } from '@vben/icons';

import { MenuBadge } from '@vben-core/menu-ui';

import { Button, message } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteMenu, getMenuTree, sortMenus } from '#/api/system/menu';
import { $t } from '#/locales';
import { refreshAccessMenus } from '#/router/access';

import { useColumns } from './data';
import Form from './modules/form.vue';

const router = useRouter();

/** 最近一次查询到的菜单树，用于拖拽后计算同级新顺序 */
let menuTree: MenuNode[] = [];

/** 菜单 id -> 父级 id，拖拽时判断是否同父级 */
const pidMap = new Map<number, number>();
/** 菜单 id -> 节点 / 层级（1 为顶级） */
const nodeMap = new Map<number, MenuNode>();
const levelMap = new Map<number, number>();

/** 表格容器（用于定位 SortableJS 要挂的 tbody） */
const gridWrapper = ref<HTMLDivElement>();
/** tbody -> Sortable 实例（vxe 固定列会渲染副本，含手柄的 tbody 都要挂） */
const sortables = new Map<HTMLElement, { destroy: () => void }>();

function buildPidMap(rows: MenuNode[], pid: number, level = 1) {
  for (const row of rows) {
    pidMap.set(row.id, pid);
    nodeMap.set(row.id, row);
    levelMap.set(row.id, level);
    buildPidMap(row.children ?? [], row.id, level + 1);
  }
}

/** 菜单树默认展开的层级数：2 = 目录 + 菜单（按钮节点默认折叠） */
const DEFAULT_EXPANDED_LEVELS = 2;

/**
 * 收集需要展开的行：前 N-1 级的父节点展开后，第 N 级节点即可见。
 * 例如 N=2 时展开所有目录，菜单节点默认可见，按钮层保持折叠。
 */
function collectExpandRows(rows: MenuNode[], level = 1): MenuNode[] {
  const result: MenuNode[] = [];
  for (const row of rows) {
    if (level < DEFAULT_EXPANDED_LEVELS) {
      result.push(row);
    }
    if (row.children?.length) {
      result.push(...collectExpandRows(row.children, level + 1));
    }
  }
  return result;
}

/** 数据装载完成后默认展开前 N 级（querySuccess 在 vxe loadData 之后触发） */
function expandDefaultLevels() {
  const rows = (gridApi.grid?.getData?.() ?? []) as MenuNode[];
  const targets = collectExpandRows(rows);
  if (targets.length > 0) {
    gridApi.grid.setTreeExpand(targets, true);
  }
}

/** 找到指定父级下的兄弟节点（按当前展示顺序） */
function findSiblings(pid: number): MenuNode[] {
  if (pid === 0) {
    return menuTree;
  }
  const stack = [...menuTree];
  while (stack.length > 0) {
    const node = stack.pop() as MenuNode;
    if (node.id === pid) {
      return node.children ?? [];
    }
    stack.push(...(node.children ?? []));
  }
  return [];
}

/** 行 DOM -> 菜单 id（vxe 在单元格上写了 rowid 属性） */
function rowIdOf(el: Element): number {
  return Number(el.querySelector('[rowid]')?.getAttribute('rowid') ?? 0);
}

/**
 * 拖拽开始时收起「当前层级」已展开的行（含被拖行自身），
 * 让同级行在 DOM 里连续，避免子行穿插导致落点判断困难；
 * 拖拽结束后会重新查询，默认展开层级会自动恢复。
 */
function collapseLevelOnDragStart(dragId: number) {
  const level = levelMap.get(dragId);
  if (!level) {
    return;
  }
  const expandedIds = [
    ...(gridWrapper.value?.querySelectorAll<HTMLElement>(
      '.vxe-body--row.is--expand-tree',
    ) ?? []),
  ]
    .map((el) => rowIdOf(el))
    .filter((id) => id > 0 && levelMap.get(id) === level);
  const targets = expandedIds
    .map((id) => nodeMap.get(id))
    .filter((node): node is MenuNode => !!node);
  if (targets.length > 0) {
    gridApi.grid.setTreeExpand(targets, false);
  }
}

/** 拖到新位置后，按 DOM 顺序取同父级节点的 id 列表 */
function siblingIdsFromDom(container: Element, pid: number): number[] {
  return [...container.children]
    .map((node) => rowIdOf(node))
    .filter((id) => pidMap.get(id) === pid && id > 0);
}

async function onDragEnd(evt: { item: HTMLElement; to: HTMLElement | null }) {
  const dragId = rowIdOf(evt.item);
  const pid = pidMap.get(dragId);
  if (!evt.to || dragId === 0 || pid === undefined) {
    return;
  }
  const ids = siblingIdsFromDom(evt.to, pid);
  const original = findSiblings(pid).map((item) => item.id);
  const changed = ids.join(',') !== original.join(',') && ids.length > 0;
  if (changed) {
    try {
      await sortMenus({ ids, pid });
    } catch {
      // 保存失败不额外提示，下面重新查询会回到服务端顺序
    }
  }
  // 拖拽改变了 DOM，无论是否落库都按服务端数据重绘一次
  onRefresh();
}

/**
 * 在表格的 tbody 上挂载 SortableJS。
 * 表格行是异步渲染的，querySuccess 时 DOM 可能还没出来，因此带一个有限重试。
 */
function ensureSortable(attempt = 0) {
  nextTick(() => attachSortable(attempt));
}

async function attachSortable(attempt: number) {
  // 清理已失效节点上的实例
  for (const [el, instance] of sortables) {
    if (!el.isConnected) {
      instance.destroy();
      sortables.delete(el);
    }
  }
  const bodies = [
    ...(gridWrapper.value?.querySelectorAll<HTMLElement>('tbody') ?? []),
  ].filter((el) => el.querySelector('[data-drag-handle]'));
  if (bodies.length === 0) {
    if (attempt < 6) {
      setTimeout(() => attachSortable(attempt + 1), 200);
    }
    return;
  }
  for (const body of bodies) {
    if (sortables.has(body)) {
      continue;
    }
    const { initializeSortable } = useSortable(body, {
      animation: 200,
      draggable: '.vxe-body--row',
      // 避免误触操作按钮与展开箭头
      filter: 'button, a, input, .vxe-cell--tree-btn',
      ghostClass: 'drag-ghost',
      handle: '[data-drag-handle]',
      onStart(evt) {
        collapseLevelOnDragStart(rowIdOf(evt.item as HTMLElement));
      },
      async onEnd(evt) {
        await onDragEnd({
          item: evt.item as HTMLElement,
          to: evt.to as HTMLElement | null,
        });
      },
      onMove(evt) {
        // 只允许同父级（同层级）之间拖动
        const dragId = rowIdOf(evt.dragged);
        const targetId = rowIdOf(evt.related as Element);
        if (dragId === 0 || targetId === 0) {
          return false;
        }
        return pidMap.get(dragId) === pidMap.get(targetId);
      },
    });
    sortables.set(body, await initializeSortable());
  }
}

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useColumns(onActionClick),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          menuTree = await getMenuTree();
          pidMap.clear();
          nodeMap.clear();
          levelMap.clear();
          buildPidMap(menuTree, 0);
          return { items: menuTree, total: 0 };
        },
        querySuccess: () => {
          expandDefaultLevels();
          ensureSortable();
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      zoom: true,
    },
    treeConfig: {
      parentField: 'pid',
      rowField: 'id',
      transform: false,
    },
  } as VxeTableGridOptions,
});

onBeforeUnmount(() => {
  for (const instance of sortables.values()) {
    instance.destroy();
  }
  sortables.clear();
});

function onActionClick({ code, row }: { code: string; row: MenuTreeNode }) {
  switch (code) {
    case 'append': {
      formDrawerApi.setData({ pid: row.id }).open();
      break;
    }
    case 'delete': {
      onDelete(row);
      break;
    }
    case 'edit': {
      formDrawerApi.setData(row).open();
      break;
    }
  }
}

function onCreate() {
  formDrawerApi.setData({ pid: 0 }).open();
}

function onDelete(row: MenuTreeNode) {
  deleteMenu(row.id)
    .then(() => {
      message.success($t('ui.actionMessage.deleteSuccess', [row.displayTitle]));
      onRefresh();
    })
    .catch(() => {});
}

function onRefresh() {
  gridApi.query();
  // 菜单数据变更后同步重新下发一次菜单，否则侧边栏徽标等信息不会更新
  refreshAccessMenus(router).catch(() => {
    // 静默失败：菜单数据保存已经成功，用户可手动刷新页面
  });
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <div ref="gridWrapper" class="h-full">
      <Grid :table-title="$t('system.menu.list')">
        <template #toolbar-tools>
          <Button
            class="!inline-flex items-center"
            type="primary"
            @click="onCreate"
          >
            <Plus class="size-5" />
            {{ $t('ui.actionTitle.create', [$t('system.menu.title')]) }}
          </Button>
        </template>
        <template #drag>
          <IconifyIcon
            class="size-4 cursor-move text-muted-foreground"
            data-drag-handle
            icon="lucide:grip-vertical"
          />
        </template>
        <template #title="{ row }">
          <div class="relative flex w-full items-center gap-1">
            <div class="size-5 shrink-0">
              <IconifyIcon
                v-if="row.type === 'BUTTON'"
                icon="carbon:security"
                class="size-full"
              />
              <IconifyIcon
                v-else-if="row.icon"
                :icon="row.icon"
                class="size-full"
              />
            </div>
            <span class="flex-auto">{{ row.displayTitle }}</span>
            <MenuBadge
              v-if="row.badgeType"
              class="menu-badge"
              :badge="row.badge"
              :badge-type="row.badgeType"
              :badge-variants="row.badgeVariants"
            />
          </div>
        </template>
      </Grid>
    </div>
  </Page>
</template>
<style lang="scss" scoped>
.drag-ghost td {
  background-color: hsl(var(--accent));
  opacity: 0.6;
}

.menu-badge {
  top: 50%;
  right: 0;
  transform: translateY(-50%);

  & > :deep(div) {
    padding-top: 0;
    padding-bottom: 0;
  }
}
</style>
