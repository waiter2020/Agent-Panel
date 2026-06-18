import { request } from '@umijs/max';

export async function storeMemory(data: {
  key: string;
  content: string;
  scope?: string;
  topologyId?: number;
  applicationId?: number;
  metadata?: Record<string, unknown>;
}) {
  const res = await request<API.ApiResponse<any>>('/api/memory', { method: 'POST', data });
  return res.data;
}

export async function searchMemory(params: {
  q: string;
  topologyId?: number;
  applicationId?: number;
  limit?: number;
}) {
  const res = await request<API.ApiResponse<any[]>>('/api/memory/search', { params });
  return res.data;
}

export async function listMemory(params?: {
  topologyId?: number;
  applicationId?: number;
  scope?: string;
}) {
  const res = await request<API.ApiResponse<any[]>>('/api/memory', { params });
  return res.data;
}

export async function deleteMemory(id: number) {
  return request(`/api/memory/${id}`, { method: 'DELETE' });
}
