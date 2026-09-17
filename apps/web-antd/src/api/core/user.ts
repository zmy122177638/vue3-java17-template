import type { UserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 无权限原因（后端 /api/user/info 下发，正常时为 null）：
 * NO_ROLE 未分配任何角色、ROLE_DISABLED 角色被禁用/失效、NO_MENU 角色没有菜单授权
 */
export type PermissionIssue = 'NO_MENU' | 'NO_ROLE' | 'ROLE_DISABLED';

/** 当前用户信息（在 vben UserInfo 基础上扩展后端下发字段） */
export interface CurrentUserInfo extends UserInfo {
  permissionIssue?: null | PermissionIssue;
}

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<CurrentUserInfo>('/user/info');
}

/**
 * 本人资料（个人中心「基本设置」）。
 * <p>只含可编辑的资料字段；账号治理字段（用户名、状态、角色）不在其中，
 * 那些只能由管理员在「用户管理」里改。
 */
export interface UserProfile {
  email?: null | string;
  nickname?: null | string;
  phone?: null | string;
  roles: string[];
  username: string;
}

export async function getUserProfileApi() {
  return requestClient.get<UserProfile>('/user/profile');
}

/**
 * 更新本人资料。
 * <p>后端返回更新后的完整资料，调用方可直接用它刷新界面，无需再 GET 一次。
 */
export async function updateUserProfileApi(params: {
  email?: string;
  nickname?: string;
  phone?: string;
}) {
  return requestClient.put<UserProfile>('/user/profile', params);
}
