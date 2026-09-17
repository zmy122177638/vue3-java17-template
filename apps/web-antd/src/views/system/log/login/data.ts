import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { LoginEvent, LoginLogItem } from '#/api/system/log';

import { $t } from '#/locales';

/**
 * 事件类型 -> 标签颜色。
 * <p>只有"成功"用绿色、"被锁定"用橙色，其余（凭据错误/账号禁用/登出）保持中性色：
 * 视觉上要能一眼分清**需要关注的事件**，而不是五彩斑斓地同等强调。
 */
const EVENT_COLORS: Record<LoginEvent, string> = {
  DISABLED: 'default',
  FAILURE: 'warning',
  LOCKED: 'error',
  LOGOUT: 'default',
  SUCCESS: 'success',
};

function eventLabel(event: LoginEvent) {
  return $t(`system.log.event.${event}`);
}

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: $t('system.log.username'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: (
          ['SUCCESS', 'FAILURE', 'DISABLED', 'LOCKED', 'LOGOUT'] as LoginEvent[]
        ).map((event) => ({ label: eventLabel(event), value: event })),
      },
      fieldName: 'event',
      label: $t('system.log.eventType'),
    },
  ];
}

export function useColumns(): VxeTableGridColumns<LoginLogItem> {
  return [
    // 可排序字段必须与后端 AuditLogServiceImpl.LOGIN_SORTABLE_FIELDS 白名单一致，
    // 否则点了列头会被后端忽略、看起来"排序没反应"
    {
      field: 'username',
      minWidth: 140,
      sortable: true,
      title: $t('system.log.username'),
    },
    {
      cellRender: {
        name: 'CellTag',
        options: (Object.keys(EVENT_COLORS) as LoginEvent[]).map((event) => ({
          color: EVENT_COLORS[event],
          label: eventLabel(event),
          value: event,
        })),
      },
      field: 'event',
      title: $t('system.log.eventType'),
      width: 110,
    },
    {
      field: 'ip',
      minWidth: 130,
      title: $t('system.log.ip'),
    },
    {
      // User-Agent 很长，用 tooltip 承载完整内容，避免撑破表格（窄屏下也能看）
      field: 'userAgent',
      minWidth: 180,
      showOverflow: 'tooltip',
      title: $t('system.log.userAgent'),
    },
    {
      // detail 是已按当前语言解析的文案，detailKey 是原始 i18n key（挂在原生 title 上供对照）
      field: 'detail',
      minWidth: 170,
      slots: { default: 'detail' },
      title: $t('system.log.detail'),
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
      field: 'createdAt',
      sortable: true,
      title: $t('system.log.createdAt'),
      width: 170,
    },
  ];
}
