import { requestClient } from '#/api/request';

/**
 * 登录日志事件类型（与后端 `AuditLogService.LoginEvent` 一一对应）。
 * <p>它是**状态枚举**而非面向用户的错误文案，所以由前端按
 * `system.log.event.<EVENT>` 做展示；后端只下发枚举名，避免为日志表逐行做文案解析。
 */
export type LoginEvent =
  | 'DISABLED'
  | 'FAILURE'
  | 'LOCKED'
  | 'LOGOUT'
  | 'SUCCESS';

export interface LoginLogItem {
  createdAt: string;
  /** 补充信息（按 Accept-Language 解析后的文案）；成功/登出为 null */
  detail?: null | string;
  /** 补充信息的原始 i18n key，用于与后端应用日志、代码对照 */
  detailKey?: null | string;
  event: LoginEvent;
  id: number;
  ip?: null | string;
  /** 请求链路标识，可复制到后端日志中检索同一次请求 */
  traceId?: null | string;
  userAgent?: null | string;
  userId?: null | number;
  username?: null | string;
}

export interface OperLogItem {
  createdAt: string;
  durationMs: number;
  /** 失败原因（按 Accept-Language 解析后的文案），成功为 null */
  errorMessage?: null | string;
  /** 失败原因的原始 i18n key，用于与后端应用日志、代码对照 */
  errorMessageKey?: null | string;
  id: number;
  ip?: null | string;
  method: string;
  module?: null | string;
  /** HTTP 状态码 */
  status: number;
  success: boolean;
  /** 操作对象主键（客体标识）；与 module 合起来即"改了哪一条数据"，新建时为 null */
  targetId?: null | number;
  /** 请求链路标识，可复制到后端日志中检索同一次请求 */
  traceId?: null | string;
  uri: string;
  userId?: null | number;
  username?: null | string;
}

export interface LogPageParams {
  /** 排序方向 asc | desc */
  order?: string;
  page: number;
  size: number;
  /** 排序字段（前端字段名，后端按白名单校验） */
  sort?: string;
}

/** 登录日志分页（只读，ADMIN 独占） */
export async function getLoginLogPage(
  params: LogPageParams & { event?: string; username?: string },
) {
  const res = await requestClient.get<{
    list: LoginLogItem[];
    total: number;
  }>('/system/logs/login/page', { params });
  return { items: res.list ?? [], total: res.total };
}

/** 操作日志分页（只读，ADMIN 独占） */
export async function getOperLogPage(
  params: LogPageParams & {
    module?: string;
    success?: number;
    username?: string;
  },
) {
  const res = await requestClient.get<{
    list: OperLogItem[];
    total: number;
  }>('/system/logs/oper/page', { params });
  return { items: res.list ?? [], total: res.total };
}
