import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemUser } from '#/api/system/user';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'keyword',
      label: $t('system.user.username'),
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
      label: $t('system.user.status'),
    },
  ];
}

export function useColumns(
  onStatusChange?: (
    newStatus: any,
    row: SystemUser,
  ) => PromiseLike<boolean | undefined>,
): VxeTableGridColumns<SystemUser> {
  return [
    // 可排序字段必须与后端 SystemUserServiceImpl.SORTABLE_FIELDS 白名单一致，
    // 否则点了列头会被后端忽略、看起来"排序没反应"
    {
      field: 'username',
      sortable: true,
      title: $t('system.user.username'),
      width: 160,
    },
    {
      field: 'nickname',
      sortable: true,
      title: $t('system.user.nickname'),
      width: 140,
    },
    {
      field: 'email',
      sortable: true,
      title: $t('system.user.email'),
      minWidth: 160,
    },
    {
      field: 'phone',
      sortable: true,
      title: $t('system.user.phone'),
      width: 140,
    },
    {
      field: 'roleNames',
      minWidth: 140,
      title: $t('system.user.role'),
      formatter: ({ cellValue }) => (cellValue ?? []).join('、'),
    },
    {
      cellRender: {
        attrs: {
          beforeChange: onStatusChange,
          // 管理员账号对普通管理员不可操作，开关直接置灰（后端仍会拦截）
          disabled: (row: SystemUser) => row.editable === false,
        },
        name: onStatusChange ? 'CellSwitch' : 'CellTag',
      },
      field: 'status',
      sortable: true,
      title: $t('system.user.status'),
      width: 90,
    },
    {
      field: 'createdAt',
      sortable: true,
      title: $t('system.user.createTime'),
      width: 170,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('system.user.operation'),
      width: 200,
    },
  ];
}

export function useFormSchema(
  roleOptions: { label: string; value: number }[] = [],
  isEdit = false,
): VbenFormSchema[] {
  /**
   * 密码字段只在新增态生成。
   * <p>编辑态是「不生成该字段」而不是用 `dependencies.show` 隐藏，原因有三：
   * 1) 后端 `UserUpdateRequest` 明确不接受 password（见其类注释），
   *    改密只走「重置密码」（管理员）与「修改密码」（本人）两个专用入口；
   * 2) 留一个填了也不生效的密码框会谎报成功，而且它没有二次确认、没有长度校验、
   *    也不会作废旧会话，与「重置密码」的安全语义相冲突；
   * 3) vben 的 `dependencies` 必须在 `triggerFields` 非空时才会生效
   *    （见 form-render/dependencies.ts 的 watch 早退分支），这里没有可依赖的触发字段。
   */
  const passwordField: VbenFormSchema = {
    component: 'InputPassword',
    fieldName: 'password',
    help: $t('system.user.passwordHelp'),
    label: $t('system.user.password'),
    // 与后端 UserCreateRequest 的 @Size(min = 6, max = 32) 对齐，避免"填完才报 400"
    rules: z
      .string()
      .min(6, $t('ui.formRules.minLength', [$t('system.user.password'), 6]))
      .max(32, $t('ui.formRules.maxLength', [$t('system.user.password'), 32])),
  };

  return [
    {
      component: 'Input',
      componentProps: { disabled: isEdit },
      fieldName: 'username',
      label: $t('system.user.username'),
      rules: 'required',
    },
    ...(isEdit ? [] : [passwordField]),
    {
      component: 'Input',
      fieldName: 'nickname',
      label: $t('system.user.nickname'),
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: $t('system.user.email'),
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: $t('system.user.phone'),
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
      label: $t('system.user.status'),
    },
    {
      component: 'Select',
      componentProps: {
        // antd Select 默认收缩包裹宽度，必须显式撑满（项目内其他表单选择框同样约定）
        class: 'w-full',
        mode: 'multiple',
        options: roleOptions,
        placeholder: $t('system.user.role'),
      },
      fieldName: 'roleIds',
      // 必填：未分配角色的账号登录后没有任何权限，属于“废号”，从源头拦截
      help: $t('system.user.roleHelp'),
      label: $t('system.user.role'),
      rules: 'required',
    },
  ];
}
