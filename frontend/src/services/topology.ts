import { request } from '@umijs/max';
import type {
  AddTopologyLinkRequest,
  AddTopologyNodeRequest,
  CreateTopologyRequest,
  TopologyDto,
  TopologyLinkDto,
} from './types/topology';

export async function listTopologies() {
  const res = await request<API.ApiResponse<TopologyDto[]>>('/api/topologies');
  return res.data;
}

export async function getTopology(id: number) {
  const res = await request<API.ApiResponse<TopologyDto>>(`/api/topologies/${id}`);
  return res.data;
}

export async function createTopology(data: CreateTopologyRequest) {
  const res = await request<API.ApiResponse<TopologyDto>>('/api/topologies', { method: 'POST', data });
  return res.data;
}

export async function updateTopology(id: number, data: CreateTopologyRequest) {
  const res = await request<API.ApiResponse<TopologyDto>>(`/api/topologies/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteTopology(id: number) {
  return request(`/api/topologies/${id}`, { method: 'DELETE' });
}

export async function addTopologyNode(topologyId: number, data: AddTopologyNodeRequest) {
  const res = await request<API.ApiResponse<TopologyDto>>(`/api/topologies/${topologyId}/nodes`, { method: 'POST', data });
  return res.data;
}

export async function removeTopologyNode(topologyId: number, applicationId: number) {
  const res = await request<API.ApiResponse<TopologyDto>>(`/api/topologies/${topologyId}/nodes/${applicationId}`, { method: 'DELETE' });
  return res.data;
}

export async function deployTopology(id: number) {
  const res = await request<API.ApiResponse<TopologyDto>>(`/api/topologies/${id}/deploy`, { method: 'POST', timeout: 300000 });
  return res.data;
}

export async function redeployTopology(id: number) {
  const res = await request<API.ApiResponse<TopologyDto>>(`/api/topologies/${id}/redeploy`, { method: 'POST', timeout: 300000 });
  return res.data;
}

export async function listTopologyLinks(topologyId: number) {
  const res = await request<API.ApiResponse<TopologyLinkDto[]>>(`/api/topologies/${topologyId}/links`);
  return res.data;
}

export async function addTopologyLink(topologyId: number, data: AddTopologyLinkRequest) {
  const res = await request<API.ApiResponse<TopologyLinkDto>>(`/api/topologies/${topologyId}/links`, { method: 'POST', data });
  return res.data;
}

export async function removeTopologyLink(topologyId: number, linkId: number) {
  return request(`/api/topologies/${topologyId}/links/${linkId}`, { method: 'DELETE' });
}
