import { request } from '@umijs/max';

export async function listProviders() {
  const res = await request<API.ApiResponse<any[]>>('/api/ai/providers');
  return res.data;
}

export async function createProvider(data: any) {
  const res = await request<API.ApiResponse<any>>('/api/ai/providers', { method: 'POST', data });
  return res.data;
}

export async function updateProvider(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/ai/providers/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteProvider(id: number) {
  return request(`/api/ai/providers/${id}`, { method: 'DELETE' });
}

export async function listModels(providerId: number) {
  const res = await request<API.ApiResponse<any[]>>('/api/ai/models', { params: { providerId } });
  return res.data;
}

export async function createModel(data: any) {
  const res = await request<API.ApiResponse<any>>('/api/ai/models', { method: 'POST', data });
  return res.data;
}

export async function updateModel(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/ai/models/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteModel(id: number) {
  return request(`/api/ai/models/${id}`, { method: 'DELETE' });
}
