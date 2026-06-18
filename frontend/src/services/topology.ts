import { request } from '@umijs/max';

export async function listTopologies() {
  const res = await request<API.ApiResponse<any[]>>('/api/topologies');
  return res.data;
}

export async function getTopology(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${id}`);
  return res.data;
}

export async function createTopology(data: any) {
  const res = await request<API.ApiResponse<any>>('/api/topologies', { method: 'POST', data });
  return res.data;
}

export async function updateTopology(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteTopology(id: number) {
  return request(`/api/topologies/${id}`, { method: 'DELETE' });
}

export async function addTopologyNode(topologyId: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${topologyId}/nodes`, { method: 'POST', data });
  return res.data;
}

export async function removeTopologyNode(topologyId: number, applicationId: number) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${topologyId}/nodes/${applicationId}`, { method: 'DELETE' });
  return res.data;
}

export async function deployTopology(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${id}/deploy`, { method: 'POST', timeout: 300000 });
  return res.data;
}

export async function redeployTopology(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${id}/redeploy`, { method: 'POST', timeout: 300000 });
  return res.data;
}

export async function listTopologyLinks(topologyId: number) {
  const res = await request<API.ApiResponse<any[]>>(`/api/topologies/${topologyId}/links`);
  return res.data;
}

export async function addTopologyLink(topologyId: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/topologies/${topologyId}/links`, { method: 'POST', data });
  return res.data;
}

export async function removeTopologyLink(topologyId: number, linkId: number) {
  return request(`/api/topologies/${topologyId}/links/${linkId}`, { method: 'DELETE' });
}
