import { apiRequest } from '../../../shared/api/client';
import type { InstancePayload } from '../model/fromInstance';

export function fetchInstanceForCanvas(id: string): Promise<InstancePayload> {
  return apiRequest<InstancePayload>(`/process-instances/${id}`);
}

export async function fetchCanvasCursor(): Promise<number> {
  const answer = await apiRequest<{ cursor: number }>('/canvas/cursor');
  return answer.cursor;
}
