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

export async function listDelegations(params: { topologyId?: number; applicationId?: number }) {
  const res = await request<API.ApiResponse<any[]>>('/api/delegations', { params });
  return res.data;
}

export async function updateDelegation(
  id: number,
  data: { status?: string; completedAt?: string; result?: Record<string, unknown> },
) {
  const res = await request<API.ApiResponse<any>>(`/api/delegations/${id}`, { method: 'PATCH', data });
  return res.data;
}
