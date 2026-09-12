import { apiRequest } from '../../../shared/api/client';

export interface EvidencePerson {
  id: string | null;
  name: string | null;

  role: string | null;
  department: string | null;
}

export interface EvidenceNode {
  id: string;
  jobId: string | null;
  jobName: string | null;

  conversationId: string | null;
  messageId: string | null;
  messageText: string | null;

  text: string | null;

  title: string | null;
  detail: string | null;

  checklist: readonly string[] | null;
  workType: string | null;

  nodeRole: string | null;
  marker: EvidencePerson;
  performer: EvidencePerson;
}

export interface EvidenceTemplate {
  id: string;
  title: string;
  status: string;
  workType: string | null;
  responsibleRole: string | null;
  checklist: readonly string[];
}

export interface EvidenceJob {
  id: string;
  name: string;
  status: string;
}

export interface EvidenceSpread {
  departments: readonly string[];
  roles: readonly string[];
  crossesDepartments: boolean;
}

export interface EvidenceBracket {
  id: string;
  jobId: string | null;
  jobName: string | null;
  workType: string | null;
  projectLabel: string | null;
  state: string | null;
  closeKind: string | null;
  outputValue: string | null;
  openedAt: string | null;
  closedAt: string | null;
  minutes: number | null;
}

export interface EvidenceWait {
  id: string;
  bracketId: string | null;
  jobId: string | null;
  jobName: string | null;
  kind: string | null;
  reason: string | null;
  openedAt: string | null;
  satisfiedAt: string | null;
  cancelledAt: string | null;
  days: number | null;
}

export interface FindingEvidence {
  nodes: readonly EvidenceNode[];
  templates: readonly EvidenceTemplate[];
  jobs: readonly EvidenceJob[];

  brackets: readonly EvidenceBracket[];
  waits: readonly EvidenceWait[];
  spread: EvidenceSpread;
}

export function fetchFindingEvidence(findingId: string): Promise<FindingEvidence> {
  return apiRequest<FindingEvidence>(`/analysis/findings/${findingId}/evidence`);
}
