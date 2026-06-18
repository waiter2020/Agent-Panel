import { request } from '@umijs/max';

export async function recordDelegation(data: {
  topologyId: number;
  parentAppId: number;
  childAppId: number;
  taskSummary: string;
  status?: string;
  startedAt?: string;
  completedAt?: string;
  result?: Record<string, unknown>;
}) {
  const res = await request<API.ApiResponse<any>>('/api/delegations', { method: 'POST', data });
  return res.data;
}

export async function listDelegations(topologyId: number) {
  const res = await request<API.ApiResponse<any[]>>('/api/delegations', { params: { topologyId } });
  return res.data;
}
