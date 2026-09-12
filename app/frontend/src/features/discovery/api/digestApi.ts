import { apiRequest } from '../../../shared/api/client';

export type DecisionKind = 'CONFIRM_A_NAME' | 'A_ROLE_STOPPED_CLICKING';

export interface DigestDecision {
  kind: DecisionKind;

  typeId: string | null;

  subject: string;

  occurrenceCount: number;
}

export interface WeeklyDigest {
  closedThisWeek: number;

  proposals: number;

  coveragePercent: number;

  decisions: DigestDecision[];
}

export function fetchDigest(): Promise<WeeklyDigest> {
  return apiRequest<WeeklyDigest>('/discovery/digest');
}

export function nameType(typeId: string, name: string): Promise<void> {
  return apiRequest<void>(`/discovery/types/${typeId}/name`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}
