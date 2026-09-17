import { requestClient } from '#/api/request';

export interface SystemRole {
  code: string;
  createdAt: string;
  id: number;
  name: string;
  remark?: null | string;
  status: number;
}

export interface RoleUpsertParams {
  code: string;
  /** 可选，未传时后端回退为角色编码 */
  menuIds?: number[];
  name?: string;
  remark?: string;
  status: number;
}

/**
 * 角色分页列表（后端 PageResult.list -> vxe proxyConfig 的 items/total 结构）。
 * <p>`sort` / `order` 来自 vxe 的 remote sort，后端只接受白名单内的字段。
 */
export async function getRolePage(params: {
  keyword?: string;
  /** 排序方向 asc | desc */
  order?: string;
  page: number;
  size: number;
  /** 排序字段（前端字段名，如 createdAt） */
  sort?: string;
  status?: number;
}) {
  const res = await requestClient.get<{
    list: SystemRole[];
    total: number;
  }>('/system/roles/page', { params });
  return { items: res.list ?? [], total: res.total };
}

export async function createRole(params: RoleUpsertParams) {
  return requestClient.post<boolean>('/system/roles', params);
}

export async function updateRole(
  id: number,
  params: Partial<RoleUpsertParams>,
) {
  return requestClient.put<boolean>(`/system/roles/${id}`, params);
}

export async function deleteRole(id: number) {
  return requestClient.delete<boolean>(`/system/roles/${id}`);
}

export async function getRoleMenuIds(id: number) {
  return requestClient.get<number[]>(`/system/roles/${id}/menu-ids`);
}

export async function saveRoleMenus(id: number, menuIds: number[]) {
  return requestClient.put<boolean>(`/system/roles/${id}/menus`, { menuIds });
}
