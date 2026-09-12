import { apiRequest } from '../../../shared/api/client';

export type DecisionKind = 'NODE_MATCH' | 'JOB_MATCH' | 'STEP_KIND' | 'DRAFT_PROCESS';

export interface PipelineDecision {
  id: string;
  findingKind: DecisionKind;

  subject: string;
  outcome: string;
  score: number | null;

  reason: string | null;
  decided: string | null;
}

export interface DecisionSource {
  kind: 'NODE' | 'JOB' | 'TEMPLATE';
  id: string;
  label: string;

  detail: string | null;

  conversationId: string | null;
  jobId: string | null;
}

export function fetchPipelineDecisions(
  kind: DecisionKind,
  limit = 25,
): Promise<readonly PipelineDecision[]> {
  return apiRequest<readonly PipelineDecision[]>(
    `/node-pipeline/runs/latest/findings?kind=${kind}&limit=${limit}`,
  );
}

export function fetchDecisionSources(decisionId: string): Promise<readonly DecisionSource[]> {
  return apiRequest<readonly DecisionSource[]>(
    `/node-pipeline/runs/decisions/${decisionId}/sources`,
  );
}
