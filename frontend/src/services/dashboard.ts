import { request } from '@umijs/max';
import type { DashboardStatsDto, PortUsageDto } from './types/app-management';

export async function getDashboardStats() {
  const res = await request<API.ApiResponse<DashboardStatsDto>>('/api/dashboard/stats');
  return res.data;
}

export async function listPortUsage() {
  const res = await request<API.ApiResponse<PortUsageDto[]>>('/api/apps/ports');
  return res.data;
}

// Backward-compatible re-exports
export { getAppHealth, getAppProxyUrl } from './appHealth';
export {
  mkdirAppFile,
  readAppFileText,
  renameAppFile,
  writeAppFileText,
} from './appVolumeFiles';
