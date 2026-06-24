import { request } from '@umijs/max';
import type { AppHealthDto } from './types/app-management';

export async function getAppHealth(id: number) {
  const res = await request<API.ApiResponse<AppHealthDto>>(`/api/apps/${id}/health`);
  return res.data;
}

export function getAppProxyUrl(appId: number, consoleKey: string) {
  return `/api/apps/${appId}/proxy/${consoleKey}/`;
}

export function getAppConsolePageUrl(appId: number, consoleKey: string) {
  return `/app/console/${appId}/${encodeURIComponent(consoleKey)}`;
}
