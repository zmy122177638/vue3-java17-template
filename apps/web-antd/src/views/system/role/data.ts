import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemRole } from '#/api/system/role';

import { $t } from '#/locales';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: $t('system.role.name'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
      },
      fieldName: 'status',
      label: $t('system.role.status'),
    },
  ];
}

/** 内置角色编码：不允许修改/删除（后端同样拦截） */
const BUILTIN_CODES = new Set(['ADMIN']);

function isBuiltin(row: SystemRole) {
  return BUILTIN_CODES.has(row.code);
}

export function useColumns(
  onActionClick: OnActionClickFn<SystemRole>,
): VxeTableGridColumns<SystemRole> {
  return [
    // 可排序字段必须与后端 SystemRoleServiceImpl.SORTABLE_FIELDS 白名单一致，
    // 否则点了列头会被后端忽略、看起来"排序没反应"
    {
      field: 'name',
      minWidth: 160,
      sortable: true,
      title: $t('system.role.name'),
    },
    {
      field: 'code',
      sortable: true,
      title: $t('system.role.code'),
      width: 160,
    },
    { field: 'remark', minWidth: 180, title: $t('system.role.remark') },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      sortable: true,
      title: $t('system.role.status'),
      width: 90,
    },
    {
      field: 'createdAt',
      sortable: true,
      title: $t('system.role.createdAt'),
      width: 170,
    },
    {
      align: 'center',
      cellRender: {
        attrs: { nameField: 'name', onClick: onActionClick },
        name: 'CellOperation',
        options: [
          { code: 'edit', show: (row: SystemRole) => !isBuiltin(row) },
          { code: 'delete', show: (row: SystemRole) => !isBuiltin(row) },
        ],
      },
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      title: $t('system.role.operation'),
      width: 160,
    },
  ];
}

export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      componentProps: {
        maxlength: 64,
      },
      fieldName: 'name',
      label: $t('system.role.name'),
      rules: 'required',
    },
    {
      component: 'Input',
      dependencies: {
        // 编辑时（表单里带 id）禁止改编码：改了会让 @RequiresRoles / 授权数据失效
        resolve: ({ values }) => ({
          componentProps: { disabled: !!values.id, maxlength: 64 },
        }),
        triggerFields: ['id'],
      },
      fieldName: 'code',
      help: $t('system.role.codeHelp'),
      label: $t('system.role.code'),
      rules: 'required',
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
      label: $t('system.role.status'),
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: $t('system.role.remark'),
    },
  ];
}
