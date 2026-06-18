import { request } from '@umijs/max';

/**
 * Umi request 默认 resolve axios 的 response.data，即后端 { code, message, data } 整包。
 * 业务层取 res.data 即可（与 services/app.ts 等保持一致）。
 */
export async function login(data: { username: string; password: string }) {
  const res = await request<API.ApiResponse<{
    accessToken: string;
    refreshToken: string;
    user: API.CurrentUser;
  }>>('/api/auth/login', {
    method: 'POST',
    data,
    skipErrorHandler: true,
  });
  return res.data;
}

export async function logout(refreshToken?: string) {
  return request('/api/auth/logout', {
    method: 'POST',
    data: refreshToken ? { refreshToken } : undefined,
  });
}

export async function refreshAccessToken(refreshToken: string) {
  const res = await request<API.ApiResponse<{ accessToken: string }>>('/api/auth/refresh', {
    method: 'POST',
    data: { refreshToken },
    skipErrorHandler: true,
  });
  return res.data;
}

export async function getSseTicket() {
  const res = await request<API.ApiResponse<{ token: string }>>('/api/auth/sse-ticket', {
    method: 'POST',
  });
  return res.data.token;
}

export async function getCurrentUser(): Promise<API.CurrentUser> {
  const res = await request<API.ApiResponse<API.CurrentUser>>('/api/auth/current', {
    skipErrorHandler: true,
  });
  const user = res.data;
  return {
    ...user,
    roles: Array.isArray(user?.roles) ? user.roles : [],
    permissions: Array.isArray(user?.permissions) ? user.permissions : [],
    menus: Array.isArray(user?.menus) ? user.menus : [],
  };
}

/** Umi Max 推荐：登录后与 getInitialState 共用 */
export async function fetchUserInfo(): Promise<API.CurrentUser | undefined> {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    return undefined;
  }
  try {
    return await getCurrentUser();
  } catch {
    return undefined;
  }
}
