import { request } from '@umijs/max';



export type RegistrySource = {
  id: 'ghcr' | 'dockerhub' | 'custom';
  name: string;
  host: string;
  description?: string;
};

export type RegistryTagsResult = {
  tags: string[];
  defaultTag?: string;
  source: 'registry' | 'fallback';
  fallback: boolean;
  message?: string;
};



export async function listRegistrySources() {
  const res = await request<API.ApiResponse<RegistrySource[]>>('/api/registry/sources');
  return res.data;
}

export async function listRegistryTags(image: string, templateId?: number) {
  const res = await request<API.ApiResponse<RegistryTagsResult>>('/api/registry/tags', {
    params: { image, templateId },
  });
  return res.data;
}



export async function listTemplates() {

  const res = await request<API.ApiResponse<any[]>>('/api/templates');

  return res.data;

}



export async function listApps() {

  const res = await request<API.ApiResponse<any[]>>('/api/apps');

  return res.data;

}



export async function getApp(id: number) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}`);

  return res.data;

}



export async function createApp(data: any) {

  const res = await request<API.ApiResponse<any>>('/api/apps', { method: 'POST', data });

  return res.data;

}



export async function updateApp(id: number, data: any) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}`, { method: 'PUT', data });

  return res.data;

}



export async function deleteApp(id: number) {

  return request(`/api/apps/${id}`, { method: 'DELETE' });

}



export async function deployApp(id: number) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}/deploy`, { method: 'POST', timeout: 300000 });

  return res.data;

}



export async function startApp(id: number) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}/start`, { method: 'POST' });

  return res.data;

}



export async function stopApp(id: number) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}/stop`, { method: 'POST' });

  return res.data;

}



export async function restartApp(id: number) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}/restart`, { method: 'POST' });

  return res.data;

}



export async function getAppStatus(id: number) {

  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}/status`);

  return res.data;

}

export async function getAppSkills(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/apps/${id}/skills`);
  return res.data;
}



export async function listAppFiles(id: number, volume: string, path?: string) {

  const res = await request<API.ApiResponse<any[]>>(`/api/apps/${id}/files`, { params: { volume, path } });

  return res.data;

}



export async function uploadAppFile(id: number, volume: string, file: File, path?: string) {

  const formData = new FormData();

  formData.append('file', file);

  formData.append('volume', volume);

  if (path) formData.append('path', path);

  return request(`/api/apps/${id}/files`, { method: 'POST', data: formData });

}



export async function deleteAppFile(id: number, volume: string, path: string) {

  return request(`/api/apps/${id}/files`, { method: 'DELETE', params: { volume, path } });

}



export function getAppFileDownloadUrl(id: number, volume: string, path: string) {

  return `/api/apps/${id}/files/download?volume=${encodeURIComponent(volume)}&path=${encodeURIComponent(path)}`;

}



export function getLogDownloadUrl(id: number, token: string) {

  return `/api/apps/${id}/logs/download?tail=5000&token=${encodeURIComponent(token)}`;

}

