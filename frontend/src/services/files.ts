import { request } from '@umijs/max';

export async function listFiles(prefix?: string, appId?: number) {
  const res = await request<API.ApiResponse<any[]>>('/api/files', { params: { prefix, appId } });
  return res.data;
}

export async function presignFile(key: string, type: 'put' | 'get' = 'put', appId?: number) {
  const res = await request<API.ApiResponse<{ url: string; key?: string }>>('/api/files/presign', {
    method: 'POST',
    data: { key, type, appId },
  });
  return res.data;
}

export async function deleteFile(key: string, appId?: number) {
  return request('/api/files', { method: 'DELETE', params: { key, appId } });
}
