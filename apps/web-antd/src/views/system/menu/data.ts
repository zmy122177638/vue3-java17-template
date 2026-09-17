import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { MenuTreeNode } from '#/api/system/menu';

import { $t } from '#/locales';

export function getMenuTypeOptions() {
  return [
    {
      color: 'processing',
      label: $t('system.menu.typeCatalog'),
      value: 'CATALOG',
    },
    { color: 'default', label: $t('system.menu.typeMenu'), value: 'MENU' },
    { color: 'error', label: $t('system.menu.typeButton'), value: 'BUTTON' },
    {
      color: 'success',
      label: $t('system.menu.typeEmbedded'),
      value: 'EMBEDDED',
    },
    { color: 'warning', label: $t('system.menu.typeLink'), value: 'LINK' },
  ];
}

export function useColumns(
  onActionClick: OnActionClickFn<MenuTreeNode>,
): VxeTableGridColumns<MenuTreeNode> {
  return [
    {
      align: 'center',
      // 拖拽手柄列（SortableJS 的 handle 选择器命中这里的图标）
      field: 'drag',
      fixed: 'left',
      slots: { default: 'drag' },
      title: '',
      width: 40,
    },
    {
      align: 'left',
      field: 'displayTitle',
      fixed: 'left',
      slots: { default: 'title' },
      title: $t('system.menu.displayTitle'),
      treeNode: true,
      width: 250,
    },
    {
      align: 'center',
      cellRender: { name: 'CellTag', options: getMenuTypeOptions() },
      field: 'type',
      title: $t('system.menu.type'),
      width: 100,
    },
    { field: 'authCode', title: $t('system.menu.authCode'), width: 200 },
    { field: 'path', title: $t('system.menu.path'), width: 180 },
    {
      field: 'component',
      formatter: ({ row }) => {
        switch (row.type) {
          case 'CATALOG':
          case 'MENU': {
            return row.component ?? '';
          }
          case 'EMBEDDED':
          case 'LINK': {
            return row.linkSrc ?? '';
          }
          default: {
            return '';
          }
        }
      },
      minWidth: 200,
      title: $t('system.menu.component'),
    },
    {
      align: 'center',
      field: 'sort',
      title: $t('system.menu.sort'),
      width: 70,
    },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      title: $t('system.menu.status'),
      width: 90,
    },
    {
      align: 'center',
      cellRender: {
        attrs: { nameField: 'displayTitle', onClick: onActionClick },
        name: 'CellOperation',
        options: [
          { code: 'append', text: $t('system.menu.append') },
          'edit',
          'delete',
        ],
      },
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      title: $t('system.menu.operation'),
      width: 200,
    },
  ];
}
