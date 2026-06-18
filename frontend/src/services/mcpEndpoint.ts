import { request } from '@umijs/max';

export async function listMcpEndpoints(applicationId?: number) {
  const res = await request<API.ApiResponse<any[]>>('/api/mcp-endpoints', {
    params: applicationId ? { applicationId } : undefined,
  });
  return res.data;
}

export async function createMcpEndpoint(data: { applicationId: number; url: string; enabled?: boolean }) {
  const res = await request<API.ApiResponse<any>>('/api/mcp-endpoints', { method: 'POST', data });
  return res.data;
}

export async function updateMcpEndpoint(id: number, data: { url?: string; enabled?: boolean }) {
  const res = await request<API.ApiResponse<any>>(`/api/mcp-endpoints/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteMcpEndpoint(id: number) {
  return request(`/api/mcp-endpoints/${id}`, { method: 'DELETE' });
}

export async function discoverMcpEndpoint(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/mcp-endpoints/${id}/discover`, { method: 'POST' });
  return res.data;
}
