import { request } from '@umijs/max';

export async function listFiles(prefix?: string) {
  const res = await request<API.ApiResponse<any[]>>('/api/files', { params: { prefix } });
  return res.data;
}

export async function presignFile(key: string, type: 'put' | 'get' = 'put') {
  const res = await request<API.ApiResponse<{ url: string }>>('/api/files/presign', {
    method: 'POST',
    data: { key, type },
  });
  return res.data;
}

export async function deleteFile(key: string) {
  return request('/api/files', { method: 'DELETE', params: { key } });
}
