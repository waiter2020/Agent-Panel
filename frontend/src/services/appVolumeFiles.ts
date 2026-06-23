import { request } from '@umijs/max';

export async function readAppFileText(id: number, volume: string, path: string) {
  const res = await request<API.ApiResponse<string>>(`/api/apps/${id}/files/text`, {
    params: { volume, path },
  });
  return res.data;
}

export async function writeAppFileText(id: number, volume: string, path: string, content: string) {
  return request(`/api/apps/${id}/files/text`, {
    method: 'PUT',
    params: { volume, path },
    data: content,
    headers: { 'Content-Type': 'text/plain' },
  });
}

export async function mkdirAppFile(id: number, volume: string, path: string) {
  return request(`/api/apps/${id}/files/mkdir`, { method: 'POST', params: { volume, path } });
}

export async function renameAppFile(id: number, volume: string, path: string, newName: string) {
  return request(`/api/apps/${id}/files/rename`, { method: 'POST', params: { volume, path, newName } });
}
