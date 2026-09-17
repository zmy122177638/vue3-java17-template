import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { OperLogItem } from '#/api/system/log';

import { $t } from '#/locales';

/** HTTP 方法 -> 标签颜色：写操作里 DELETE 最需要被一眼看到 */
const METHOD_COLORS: Record<string, string> = {
  DELETE: 'error',
  PATCH: 'warning',
  POST: 'success',
  PUT: 'processing',
};

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: $t('system.log.username'),
    },
    {
      component: 'Input',
      fieldName: 'module',
      label: $t('system.log.module'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('system.log.success'), value: 1 },
          { label: $t('system.log.failure'), value: 0 },
        ],
      },
      fieldName: 'success',
      label: $t('system.log.result'),
    },
  ];
}

export function useColumns(): VxeTableGridColumns<OperLogItem> {
  return [
    // 可排序字段必须与后端 AuditLogServiceImpl.OPER_SORTABLE_FIELDS 白名单一致
    {
      field: 'username',
      minWidth: 130,
      sortable: true,
      title: $t('system.log.username'),
    },
    {
      field: 'module',
      minWidth: 110,
      sortable: true,
      title: $t('system.log.module'),
    },
    {
      cellRender: {
        name: 'CellTag',
        options: Object.entries(METHOD_COLORS).map(([method, color]) => ({
          color,
          label: method,
          value: method,
        })),
      },
      field: 'method',
      title: $t('system.log.method'),
      width: 100,
    },
    {
      // 路径通常较长，用 tooltip 承载完整值，窄屏也不撑破布局
      field: 'uri',
      minWidth: 200,
      showOverflow: 'tooltip',
      title: $t('system.log.uri'),
    },
    {
      // 操作对象主键（后端从路径解析）：与左侧 module 列合起来即"改了哪一条数据"。
      // 新建、或 /menus/sort 这类无 id 的动作为空，用连字符占位避免"看起来像没记录"
      field: 'targetId',
      formatter: ({ cellValue }) =>
        typeof cellValue === 'number' ? `#${cellValue}` : '—',
      title: $t('system.log.target'),
      width: 110,
    },
    {
      // 请求链路标识：与后端应用日志里的 [traceId] 同值，
      // 排查时把它复制出去即可捞出同一次请求的完整轨迹
      field: 'traceId',
      minWidth: 220,
      showOverflow: 'tooltip',
      title: $t('system.log.traceId'),
    },
    {
      cellRender: {
        name: 'CellTag',
        options: [
          { color: 'success', label: $t('system.log.success'), value: true },
          { color: 'error', label: $t('system.log.failure'), value: false },
        ],
      },
      field: 'success',
      title: $t('system.log.result'),
      width: 100,
    },
    {
      // 后端下发两个字段：errorMessage 已按当前语言解析（直接展示），
      // errorMessageKey 是原始 i18n key（挂在原生 title 上，便于与后端日志/代码对照）。
      // 成功时两者都为空，用连字符占位避免"空列看起来像没记录"（渲染见 index.vue 的 slot）
      field: 'errorMessage',
      minWidth: 180,
      slots: { default: 'errorMessage' },
      title: $t('system.log.errorMessage'),
    },
    {
      field: 'status',
      sortable: true,
      title: $t('system.log.status'),
      width: 90,
    },
    {
      field: 'durationMs',
      formatter: ({ cellValue }) => `${cellValue ?? 0} ms`,
      sortable: true,
      title: $t('system.log.duration'),
      width: 110,
    },
    {
      field: 'ip',
      minWidth: 130,
      title: $t('system.log.ip'),
    },
    {
      field: 'createdAt',
      sortable: true,
      title: $t('system.log.createdAt'),
      width: 170,
    },
  ];
}
