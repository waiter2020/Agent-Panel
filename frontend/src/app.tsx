import * as AntdIcons from '@ant-design/icons';
import { fetchUserInfo, logout, refreshAccessToken } from '@/services/auth';
import { logger } from '@/utils/logger';
import { history, RunTimeLayoutConfig, RequestConfig } from '@umijs/max';
import { message } from 'antd';
import React from 'react';

const LOGIN_PATH = '/login';

function normalizeCurrentUser(user?: API.CurrentUser | null): API.CurrentUser | undefined {
  if (!user) {
    return undefined;
  }
  return {
    ...user,
    roles: Array.isArray(user.roles) ? user.roles : [],
    permissions: Array.isArray(user.permissions) ? user.permissions : [],
    menus: Array.isArray(user.menus) ? user.menus : [],
  };
}

function resolveMenuIcon(icon?: string): React.ReactNode | undefined {
  if (!icon) {
    return undefined;
  }
  const IconComponent = (AntdIcons as Record<string, React.ComponentType>)[icon];
  return IconComponent ? React.createElement(IconComponent) : undefined;
}

function menuNodeToRoute(node: API.MenuNode): any {
  const route: any = {
    name: node.name,
    path: node.path,
    icon: resolveMenuIcon(node.icon),
  };
  if (node.component) {
    route.component = node.component;
  }
  if (node.children?.length) {
    route.routes = node.children.map(menuNodeToRoute);
  }
  return route;
}

function redirectToLogin() {
  if (history.location.pathname !== LOGIN_PATH) {
    history.replace(LOGIN_PATH);
  }
}

let refreshing: Promise<string | null> | null = null;

async function tryRefreshToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    return null;
  }
  if (!refreshing) {
    refreshing = refreshAccessToken(refreshToken)
      .then((data) => {
        localStorage.setItem('accessToken', data.accessToken);
        return data.accessToken;
      })
      .catch(() => {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        return null;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

async function fetchUserInfoWithRefresh(): Promise<API.CurrentUser | undefined> {
  const user = await fetchUserInfo();
  if (user) {
    return normalizeCurrentUser(user);
  }
  const newToken = await tryRefreshToken();
  if (!newToken) {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    return undefined;
  }
  const retryUser = await fetchUserInfo();
  if (!retryUser) {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    return undefined;
  }
  return normalizeCurrentUser(retryUser);
}

export async function getInitialState(): Promise<API.InitialState> {
  const state: API.InitialState = { fetchUserInfo: fetchUserInfoWithRefresh };

  // 登录页不拉取用户信息，避免无 token 时多余 401
  if (history.location.pathname === LOGIN_PATH) {
    return state;
  }

  const currentUser = await fetchUserInfoWithRefresh();
  return { ...state, currentUser };
}

export const request: RequestConfig = {
  timeout: 30000,
  requestInterceptors: [
    (url: string, options: any) => {
      const token = localStorage.getItem('accessToken');
      return {
        url,
        options: {
          ...options,
          headers: {
            ...options.headers,
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        },
      };
    },
  ],
  responseInterceptors: [
    [
      (response: any) => {
        const body = response?.data;
        if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 0) {
          const error: any = new Error(body.message || '请求失败');
          error.data = body;
          throw error;
        }
        return response;
      },
      async (error: any) => {
        const { response, config } = error;
        const method = config?.method?.toUpperCase() ?? 'GET';
        const url = config?.url ?? '';
        const status = response?.status;
        const message = response?.data?.message ?? error?.message ?? '请求失败';

        if (status !== 401) {
          logger.error('API 请求失败', { method, url, status, message });
          if (process.env.NODE_ENV === 'development' && response?.data) {
            logger.debug('响应详情', response.data);
          }
        }

        if (
          response?.status === 401 &&
          !config?.skipErrorHandler &&
          !config?.url?.includes('/api/auth/refresh')
        ) {
          const newToken = await tryRefreshToken();
          if (newToken) {
            config.headers = { ...config.headers, Authorization: `Bearer ${newToken}` };
            const { request: umiRequest } = await import('@umijs/max');
            return umiRequest(config.url, config);
          }
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
          redirectToLogin();
        }
        throw error;
      },
    ],
  ],
};

export const layout: RunTimeLayoutConfig = ({ initialState }) => ({
  logo: '/logo.svg',
  menu: { locale: false },
  menuDataRender: () => {
    const menus = initialState?.currentUser?.menus ?? [];
    if (!menus.length) {
      return [];
    }
    return menus.map(menuNodeToRoute);
  },
  onPageChange: () => {
    const { pathname } = history.location;
    if (pathname === LOGIN_PATH) {
      return;
    }
    if (!initialState?.currentUser) {
      redirectToLogin();
    }
  },
  logout: async () => {
    const refreshToken = localStorage.getItem('refreshToken') || undefined;
    await logout(refreshToken);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    message.success('已退出登录');
    history.replace(LOGIN_PATH);
  },
});
