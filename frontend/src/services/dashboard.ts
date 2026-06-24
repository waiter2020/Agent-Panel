import { request } from '@umijs/max';
import type { DashboardStatsDto, PortUsageDto } from './types/app-management';

const defaultDashboardStats: DashboardStatsDto = {
  totalApps: 0,
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

function unwrapApiData<T>(payload: unknown): T {
  const value = payload as { code?: number; data?: unknown } | undefined;
  if (value && typeof value === 'object' && 'code' in value && 'data' in value) {
    return value.data as T;
  }

  const nested = value?.data as { code?: number; data?: unknown } | undefined;
  if (nested && typeof nested === 'object' && 'code' in nested && 'data' in nested) {
    return nested.data as T;
  }

  if (
    value &&
    typeof value === 'object' &&
    'data' in value &&
    ('status' in value || 'headers' in value || 'config' in value)
  ) {
    return value.data as T;
  }

  return payload as T;
}

function normalizeDashboardStats(payload: unknown): DashboardStatsDto {
  const stats = unwrapApiData<Partial<DashboardStatsDto> | undefined>(payload);
  return {
    ...defaultDashboardStats,
    ...(stats ?? {}),
    byTemplate: stats?.byTemplate ?? {},
    byRuntime: stats?.byRuntime ?? {},
    recentApps: stats?.recentApps ?? [],
    portUsage: stats?.portUsage ?? [],
  };
}

export async function getDashboardStats(): Promise<DashboardStatsDto> {
  const res = await request<API.ApiResponse<DashboardStatsDto>>('/api/dashboard/stats');
  return normalizeDashboardStats(res);
}

export async function listPortUsage(): Promise<PortUsageDto[]> {
  const res = await request<API.ApiResponse<PortUsageDto[]>>('/api/apps/ports');
  return unwrapApiData<PortUsageDto[]>(res) ?? [];
}

// Backward-compatible re-exports
export { getAppHealth, getAppProxyUrl } from './appHealth';
export {
  mkdirAppFile,
  readAppFileText,
  renameAppFile,
  writeAppFileText,
} from './appVolumeFiles';
