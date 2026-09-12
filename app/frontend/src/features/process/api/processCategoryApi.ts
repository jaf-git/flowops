import { apiRequest } from '../../../shared/api/client';

export interface ProcessCategory {
  id: string;
  name: string;

  runCount: number;
}

export interface ProcessCategories {
  categories: ProcessCategory[];
  filings: Record<string, string>;
}

export function fetchProcessCategories(): Promise<ProcessCategories> {
  return apiRequest<ProcessCategories>('/process-categories');
}

export function createProcessCategory(name: string): Promise<{ id: string }> {
  return apiRequest<{ id: string }>('/process-categories', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export function deleteProcessCategory(id: string): Promise<void> {
  return apiRequest<void>(`/process-categories/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function fileRunUnder(instanceId: string, categoryId: string | null): Promise<void> {
  return apiRequest<void>(`/process-categories/runs/${encodeURIComponent(instanceId)}`, {
    method: 'PUT',
    body: JSON.stringify({ categoryId }),
  });
}

export function archiveRun(instanceId: string): Promise<void> {
  return apiRequest<void>(`/process-instances/${encodeURIComponent(instanceId)}/archive`, {
    method: 'POST',
  });
}

export function restoreRun(instanceId: string): Promise<void> {
  return apiRequest<void>(`/process-instances/${encodeURIComponent(instanceId)}/archive`, {
    method: 'DELETE',
  });
}
