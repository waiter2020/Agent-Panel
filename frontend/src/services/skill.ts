import { request } from '@umijs/max';

export async function listSkills(params: { topologyId?: number; applicationId?: number }) {
  const res = await request<API.ApiResponse<any[]>>('/api/skills', { params });
  return res.data;
}

export async function getSkill(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/skills/${id}`);
  return res.data;
}

export async function createSkill(data: {
  topologyId: number;
  name: string;
  description?: string;
  content?: string;
  applicationId?: number;
}, file?: File) {
  if (file) {
    const form = new FormData();
    form.append('data', JSON.stringify(data));
    form.append('file', file);
    const res = await request<API.ApiResponse<any>>('/api/skills', { method: 'POST', data: form });
    return res.data;
  }
  const res = await request<API.ApiResponse<any>>('/api/skills', { method: 'POST', data });
  return res.data;
}

export async function updateSkill(id: number, data: {
  name?: string;
  description?: string;
  content?: string;
  applicationId?: number;
}, file?: File) {
  if (file) {
    const form = new FormData();
    form.append('data', JSON.stringify(data));
    form.append('file', file);
    const res = await request<API.ApiResponse<any>>(`/api/skills/${id}`, { method: 'PUT', data: form });
    return res.data;
  }
  const res = await request<API.ApiResponse<any>>(`/api/skills/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteSkill(id: number) {
  return request(`/api/skills/${id}`, { method: 'DELETE' });
}

export async function downloadSkill(id: number) {
  const res = await request<API.ApiResponse<{ url: string }>>(`/api/skills/${id}/download`);
  return res.data;
}

export async function notifySkillReload(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/skills/${id}/notify-reload`, { method: 'POST' });
  return res.data;
}

export async function notifyTopologySkillsReload(topologyId: number) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${topologyId}/notify-skills-reload`, { method: 'POST' });
  return res.data;
}
