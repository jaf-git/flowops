import { apiRequest } from '../../../shared/api/client';

import type { Stage } from './analysisApi';

export interface StuckGroup {
  itemId: string;

  itemKind: 'NODE' | 'JOB' | 'BRACKET' | 'RUN';
  lastStage: Stage;

  reason: string;
  count: number;
}

export async function fetchStuck(): Promise<StuckGroup[]> {
  return apiRequest<StuckGroup[]>('/node-pipeline/runs/latest/stuck').catch(() => []);
}
