import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data);
}

/**
 * 刷新accessToken
 *
 * 注意 `post(url, data, config)` 的第二个参数是**请求体**，`withCredentials` 属于 config，
 * 必须放在第三个参数上。放在第二个参数里会变成一个 `{"withCredentials":true}` 的请求体，
 * 而 axios 的 withCredentials 仍是默认的 false —— 同源部署（dev 走 vite proxy、
 * 生产走 Nginx 同源反代）时看不出问题，一旦跨域部署就会因为不带 Cookie 而陷入 401 循环。
 * 这里显式传 null 作为 data。
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/auth/refresh',
    null,
    { withCredentials: true },
  );
}

/**
 * 退出登录
 *
 * 必须带上 accessToken：登出接口本身在白名单里（令牌过期也要能登出），
 * 但后端要靠请求头里的令牌才能删掉 Redis 中的 `auth:access:{token}`。
 * 不带的话服务端令牌会一直有效到 TTL 结束（默认 120 分钟），
 * 也就是"点了登出，但被窃取的令牌还能继续用"。
 */
export async function logoutApi(accessToken?: null | string) {
  return baseRequestClient.post('/auth/logout', null, {
    headers: accessToken
      ? { Authorization: `Bearer ${accessToken}` }
      : undefined,
    withCredentials: true,
  });
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}

/**
 * 修改当前登录用户密码
 */
export async function changePasswordApi(data: {
  newPassword: string;
  oldPassword: string;
}) {
  return requestClient.put('/auth/password', data);
}
