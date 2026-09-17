import { requestClient } from '#/api/request';

export interface SystemUser {
  createdAt: string;
  /** 当前操作者是否可维护该用户（管理员账号对普通管理员为 false） */
  editable?: boolean;
  email?: null | string;
  id: number;
  nickname?: null | string;
  phone?: null | string;
  roleIds: number[];
  roleNames: string[];
  status: number;
  username: string;
}

/** 当前操作者可分配的角色（非超管不含内置 ADMIN 角色） */
export interface AssignableRole {
  code: string;
  id: number;
  name: string;
  status: number;
}

export interface UserCreateParams {
  email?: string;
  nickname?: string;
  password: string;
  phone?: string;
  /** 后端要求至少一个角色，避免创建出“能登录但无任何权限”的账号 */
  roleIds: number[];
  status?: number;
  username: string;
}

export interface UserUpdateParams {
  email?: string;
  nickname?: string;
  phone?: string;
  roleIds: number[];
  status?: number;
}

/**
 * 用户分页列表（后端 PageResult.list -> vxe proxyConfig 的 items/total 结构）。
 * <p>`sort` / `order` 来自 vxe 的 remote sort，后端只接受白名单内的字段。
 */
export async function getUserPage(params: {
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
    list: SystemUser[];
    total: number;
  }>('/system/users/page', { params });
  return { items: res.list ?? [], total: res.total };
}

export async function createUser(params: UserCreateParams) {
  return requestClient.post<boolean>('/system/users', params);
}

export async function updateUser(id: number, params: UserUpdateParams) {
  return requestClient.put<boolean>(`/system/users/${id}`, params);
}

export async function updateUserStatus(id: number, status: number) {
  return requestClient.put<boolean>(`/system/users/${id}/status`, { status });
}

export async function resetUserPassword(id: number, password: string) {
  return requestClient.put<boolean>(`/system/users/${id}/password`, {
    password,
  });
}

export async function deleteUser(id: number) {
  return requestClient.delete<boolean>(`/system/users/${id}`);
}

export async function getUserRoleIds(id: number) {
  return requestClient.get<number[]>(`/system/users/${id}/role-ids`);
}

/**
 * 当前操作者可分配的角色（用户表单角色下拉）。
 * 普通管理员的返回结果不含内置 ADMIN 角色，因此界面上无法给他人授予管理员权限。
 */
export async function getAssignableRoles() {
  return requestClient.get<AssignableRole[]>('/system/users/assignable-roles');
}
