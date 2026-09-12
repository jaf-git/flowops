import { apiRequest } from '../../../shared/api/client';

export interface TaskCategory {
  id: string;
  name: string;

  taskCount: number;
}

export interface TaskCategories {
  categories: TaskCategory[];
  filings: Record<string, string>;
}

export function fetchTaskCategories(): Promise<TaskCategories> {
  return apiRequest<TaskCategories>('/task-categories');
}

export function createTaskCategory(name: string): Promise<{ id: string }> {
  return apiRequest<{ id: string }>('/task-categories', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export function renameTaskCategory(id: string, name: string): Promise<void> {
  return apiRequest<void>(`/task-categories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

export function deleteTaskCategory(id: string): Promise<void> {
  return apiRequest<void>(`/task-categories/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function fileTaskUnder(taskId: string, categoryId: string | null): Promise<void> {
  return apiRequest<void>(`/task-categories/tasks/${encodeURIComponent(taskId)}`, {
    method: 'PUT',
    body: JSON.stringify({ categoryId }),
  });
}
