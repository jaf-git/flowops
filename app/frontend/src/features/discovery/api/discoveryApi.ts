import { apiRequest } from '../../../shared/api/client';

export type Direction = 'REQUEST' | 'COMPLETION' | 'STANDALONE' | 'QUERY';

export type OutputType = 'TEXT' | 'DESIGN' | 'REPORT' | 'SCHEDULING' | 'NONE';

export type WaitingOn = 'CLIENT' | 'COLLEAGUE' | 'SUPPLIER' | 'APPROVAL';

export type NudgeAnswer = 'DONE' | 'STILL_GOING' | 'DROPPED' | 'WAS_A_QUESTION';

export type NodeKind = 'JOB_START' | 'WORK' | 'JOB_END';

export type WorkNodeState =
  | 'MARKED'
  | 'ASSIGNED'
  | 'SELF'
  | 'BOUNCED'
  | 'IN_PROGRESS'
  | 'BLOCKED'
  | 'COMPLETED'
  | 'CLOSED'
  | 'LAPSED'
  | 'QUERY'
  | 'ANSWERED';

export type TrackState = 'OPEN' | 'ACTIVE' | 'DORMANT' | 'DISRUPTED' | 'CLOSED';

export type KeyBasis = 'ROLE_PAIR' | 'PERFORMER';

export interface WorkNodeRow {
  id: string;
  jobId: string;

  trackId: string | null;

  text: string;

  detail: string | null;
  direction: Direction;
  kind: NodeKind;
  state: WorkNodeState;

  outputType: OutputType | null;
  creatorRole: string | null;
  performerRole: string | null;
  createdAt: string;
  startedAt: string | null;
  closedAt: string | null;

  templated: boolean;
}

export interface JobRow {
  id: string;
  name: string;
  counterpartyId: string | null;
  counterpartyName: string | null;
  openedAt: string;
  closedAt: string | null;

  lastActivityAt: string;
}

export interface TrackRow {
  id: string;
  jobId: string;

  fromRole: string | null;
  toRole: string | null;
  keyBasis: KeyBasis;
  state: TrackState;
  openedAt: string;
  closedAt: string | null;

  completeness: 'COMPLETE' | 'PARTIAL' | 'START_ONLY' | null;
}

export interface NodePhaseRow {
  workNodeId: string;
  phase: 'WORK' | 'EXTERNAL_WAIT' | 'INTERNAL_WAIT' | 'REVIEW';
  startedAt: string;
  endedAt: string | null;

  waitingOn: WaitingOn | null;
}

export interface MarkedResponse {
  nodeId: string;
  jobId: string;
  trackId: string | null;
  weaklyKeyed: boolean;
}

export interface OpenJobRequest {
  messageId: string;
  name: string;

  projectLabel?: string;

  evenThoughOneIsOpen?: boolean;

  counterpartyId?: string;
}

export interface MarkMessageRequest {
  messageId: string;
  jobId: string;
  direction: Direction;
  performerId: string | null;
}

export function openJob(request: OpenJobRequest): Promise<MarkedResponse> {
  return apiRequest<MarkedResponse>('/discovery/jobs', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export interface JobOption {
  jobId: string;
  name: string;
  guessed: boolean;
}

export function fetchJobsForConversation(
  conversationId?: string,

  describing = false,
): Promise<JobOption[]> {
  const query = new URLSearchParams();
  if (conversationId !== undefined) {
    query.set('conversationId', conversationId);
  }
  if (describing) {
    query.set('describing', 'true');
  }

  const suffix = query.size === 0 ? '' : `?${query.toString()}`;
  return apiRequest<JobOption[]>(`/discovery/jobs${suffix}`);
}

export function deleteNode(nodeId: string): Promise<void> {
  return apiRequest<void>(`/discovery/nodes/${nodeId}`, { method: 'DELETE' });
}

export function relinkNode(nodeId: string, jobId: string): Promise<MarkedResponse> {
  return apiRequest<MarkedResponse>(`/discovery/nodes/${nodeId}/job`, {
    method: 'PATCH',
    body: JSON.stringify({ jobId }),
  });
}

export function markMessage(request: MarkMessageRequest): Promise<MarkedResponse> {
  return apiRequest<MarkedResponse>('/discovery/nodes', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export interface NodeProgress {
  nodeId: string;
  state: WorkNodeState;

  outputType: OutputType | null;

  openPhase: string | null;

  waitingOn: WaitingOn | null;

  pairedWith: string | null;

  asksForAnOutput: boolean;
}

export function recordOutput(nodeId: string, outputType: OutputType): Promise<NodeProgress> {
  return apiRequest<NodeProgress>(`/discovery/nodes/${nodeId}/output`, {
    method: 'POST',
    body: JSON.stringify({ outputType }),
  });
}

export function blockNode(nodeId: string, waitingOn: WaitingOn): Promise<NodeProgress> {
  return apiRequest<NodeProgress>(`/discovery/nodes/${nodeId}/block`, {
    method: 'POST',
    body: JSON.stringify({ waitingOn }),
  });
}

export function resumeNode(nodeId: string): Promise<NodeProgress> {
  return apiRequest<NodeProgress>(`/discovery/nodes/${nodeId}/resume`, { method: 'POST' });
}

export interface NudgeSubject {
  nodeId: string;
  jobId: string;

  trackId: string | null;

  text: string;
  state: WorkNodeState;
}

export async function fetchNudge(): Promise<NudgeSubject | null> {
  return (await apiRequest<NudgeSubject | undefined>('/discovery/nudge')) ?? null;
}

export function answerNudge(nodeId: string, answer: NudgeAnswer): Promise<NodeProgress> {
  return apiRequest<NodeProgress>(`/discovery/nodes/${nodeId}/nudge-answer`, {
    method: 'POST',
    body: JSON.stringify({ answer }),
  });
}

export interface TrackEnding {
  completeness: 'COMPLETE' | 'PARTIAL' | 'START_ONLY';
  closeReason: string;
}

export function endThread(trackId: string): Promise<TrackEnding> {
  return apiRequest<TrackEnding>(`/discovery/tracks/${trackId}/end`, { method: 'POST' });
}

export interface NodeEnrichment {
  nodeId: string;
  title: string | null;
  detail: string | null;
  checklist: readonly string[] | null;

  accepted: boolean;

  correctable: boolean;
}

export interface EnrichNodeRequest {
  title?: string;
  detail?: string;
  checklist?: readonly string[];
}

export function enrichNode(nodeId: string, fields: EnrichNodeRequest): Promise<NodeEnrichment> {
  return apiRequest<NodeEnrichment>(`/discovery/nodes/${nodeId}/enrichment`, {
    method: 'PATCH',
    body: JSON.stringify(fields),
  });
}

export interface NodeMove {
  from: WorkNodeState | null;
  to: WorkNodeState;

  actorId: string | null;
  reason: string | null;
  occurredAt: string;
}

export interface NodeTrail {
  nodeId: string;
  recordedFromTheStart: boolean;
  moves: readonly NodeMove[];
}

export function fetchNodeTrail(nodeId: string): Promise<NodeTrail> {
  return apiRequest<NodeTrail>(`/discovery/nodes/${nodeId}/trail`);
}
