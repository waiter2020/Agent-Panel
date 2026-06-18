import { request } from '@umijs/max';

export async function listUsers(params: { page?: number; pageSize?: number }) {
  const res = await request<API.ApiResponse<API.PageResult<any>>>('/api/users', { params });
  return res.data;
}

export async function createUser(data: any) {
  const res = await request<API.ApiResponse<any>>('/api/users', { method: 'POST', data });
  return res.data;
}

export async function updateUser(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/users/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteUser(id: number) {
  return request(`/api/users/${id}`, { method: 'DELETE' });
}

export async function listRoles() {
  const res = await request<API.ApiResponse<any[]>>('/api/roles');
  return res.data;
}

export async function updateRole(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/roles/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function listMenus() {
  const res = await request<API.ApiResponse<any[]>>('/api/menus');
  return res.data;
}

export async function listPermissions() {
  const res = await request<API.ApiResponse<any[]>>('/api/permissions');
  return res.data;
}

export async function listAuditLogs(params: { page?: number; pageSize?: number }) {
  const res = await request<API.ApiResponse<API.PageResult<any>>>('/api/audit-logs', { params });
  return res.data;
}

export async function getSettings() {
  const res = await request<API.ApiResponse<Record<string, string>>>('/api/settings');
  return res.data;
}

export async function updateSettings(data: Record<string, string>) {
  const res = await request<API.ApiResponse<Record<string, string>>>('/api/settings', { method: 'PUT', data });
  return res.data;
}

export async function listApiKeys() {
  const res = await request<API.ApiResponse<any[]>>('/api/api-keys');
  return res.data;
}

export async function createApiKey(data: any) {
  const res = await request<API.ApiResponse<any>>('/api/api-keys', { method: 'POST', data });
  return res.data;
}

export async function updateApiKey(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/api-keys/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteApiKey(id: number) {
  return request(`/api/api-keys/${id}`, { method: 'DELETE' });
}

export async function rotateApiKey(id: number) {
  const res = await request<API.ApiResponse<any>>(`/api/api-keys/${id}/rotate`, { method: 'POST' });
  return res.data;
}

export async function listTenants() {
  const res = await request<API.ApiResponse<any[]>>('/api/tenants');
  return res.data;
}

export async function createTenant(data: any) {
  const res = await request<API.ApiResponse<any>>('/api/tenants', { method: 'POST', data });
  return res.data;
}

export async function updateTenant(id: number, data: any) {
  const res = await request<API.ApiResponse<any>>(`/api/tenants/${id}`, { method: 'PUT', data });
  return res.data;
}

export async function deleteTenant(id: number) {
  return request(`/api/tenants/${id}`, { method: 'DELETE' });
}
