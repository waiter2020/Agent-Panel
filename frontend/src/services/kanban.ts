import { request } from '@umijs/max';
import type { TaskBoardDto, TaskItemDto } from './types/app-management';

export async function listKanbanBoards() {
  const res = await request<API.ApiResponse<TaskBoardDto[]>>('/api/kanban/boards');
  return res.data;
}

export async function getKanbanBoard(boardId: number) {
  const res = await request<API.ApiResponse<TaskBoardDto>>(`/api/kanban/boards/${boardId}`);
  return res.data;
}

export async function syncKanbanBoard(boardId: number) {
  const res = await request<API.ApiResponse<void>>(`/api/kanban/boards/${boardId}/sync`, { method: 'POST' });
  return res.data;
}

export async function moveKanbanTask(taskId: number, columnId: number, orderNo?: number) {
  const res = await request<API.ApiResponse<TaskItemDto>>(`/api/kanban/tasks/${taskId}/move`, {
    method: 'POST',
    data: { columnId, orderNo },
  });
  return res.data;
}
