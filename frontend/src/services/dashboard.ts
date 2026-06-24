import { request } from '@umijs/max';
import type { DashboardStatsDto, PortUsageDto } from './types/app-management';

export const defaultDashboardStats: DashboardStatsDto = {
  totalApps: 0,
  createdApps: 0,
  runningApps: 0,
  stoppedApps: 0,
  deployingApps: 0,
  errorApps: 0,
  byTemplate: {},
  byRuntime: {},
  exposedPorts: 0,
  internalPorts: 0,
  portConflicts: 0,
  topologyCount: 0,
  deployedTopologies: 0,
  recentApps: [],
  portUsage: [],
};

export async function getDashboardStats(): Promise<DashboardStatsDto> {
  const res = await request<API.ApiResponse<DashboardStatsDto>>('/api/dashboard/stats');
  return res.data ?? defaultDashboardStats;
}

export async function listPortUsage(): Promise<PortUsageDto[]> {
  const res = await request<API.ApiResponse<PortUsageDto[]>>('/api/apps/ports');
  return res.data ?? [];
}

// Backward-compatible re-exports
export { getAppHealth, getAppProxyUrl } from './appHealth';
export {
  mkdirAppFile,
  readAppFileText,
  renameAppFile,
  writeAppFileText,
} from './appVolumeFiles';
