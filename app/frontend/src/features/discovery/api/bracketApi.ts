import { apiRequest } from '../../../shared/api/client';

import type {
  Activity,
  Client,
  ConversationBracket,
  ConversationWait,
  MessageMark,
  OutputKind,
  WaitKind,
} from '../model/bracket';

export * from '../model/bracket';

export interface CloseBracketRequest {
  kind: 'DELIVERED' | 'DONE' | 'DROPPED';

  outputKind?: OutputKind;
  outputValue?: string;

  reason?: string;
}

export interface CloseBracketResponse {
  bracketId: string;
  endedAs: string;

  released: number;

  warned: number;
  childrenClosed: number;
}

export interface DeclareWaitRequest {
  kind: WaitKind;
  onBracketId?: string;
  reason?: string;
  expectedBy?: string;
}

export interface DeclareWaitResponse {
  waitId: string;
  kind: WaitKind;
  external: boolean;

  alreadySatisfied: boolean;
}

export interface HandOverRequest {
  newPerformerId: string;

  messageId: string;

  causedByDeactivation: boolean;
}

export interface HandOverResponse {
  closedBracketId: string;
  successorBracketId: string;

  workType: string;

  disrupted: boolean;

  reTargeted: number;
  released: number;
}

export function fetchConversationWork(conversationId: string): Promise<ConversationBracket[]> {
  return apiRequest<ConversationBracket[]>(
    `/discovery/brackets/conversation/${encodeURIComponent(conversationId)}`,
  );
}

export function fetchConversationWaits(conversationId: string): Promise<ConversationWait[]> {
  return apiRequest<ConversationWait[]>(
    `/discovery/brackets/conversation/${encodeURIComponent(conversationId)}/waits`,
  );
}

export function closeBracket(
  bracketId: string,
  request: CloseBracketRequest,
): Promise<CloseBracketResponse> {
  return apiRequest<CloseBracketResponse>(
    `/discovery/brackets/${encodeURIComponent(bracketId)}/close`,
    { method: 'POST', body: JSON.stringify(request) },
  );
}

export async function handOverBracket(
  bracketId: string,
  request: HandOverRequest,
): Promise<HandOverResponse | undefined> {
  return apiRequest<HandOverResponse | undefined>(
    `/discovery/brackets/${encodeURIComponent(bracketId)}/hand-over`,
    { method: 'POST', body: JSON.stringify(request) },
  );
}

export function declareWait(
  bracketId: string,
  request: DeclareWaitRequest,
): Promise<DeclareWaitResponse> {
  return apiRequest<DeclareWaitResponse>(
    `/discovery/brackets/${encodeURIComponent(bracketId)}/wait`,
    { method: 'POST', body: JSON.stringify(request) },
  );
}

export function fetchConversationMarks(conversationId: string): Promise<MessageMark[]> {
  return apiRequest<MessageMark[]>(
    `/discovery/brackets/conversation/${encodeURIComponent(conversationId)}/marks`,
  );
}

export function endWait(waitId: string, how: 'arrived' | 'withdraw'): Promise<void> {
  return apiRequest<void>(`/discovery/brackets/wait/${encodeURIComponent(waitId)}/${how}`, {
    method: 'POST',
    body: '{}',
  });
}

export function fetchClients(): Promise<Client[]> {
  return apiRequest<Client[]>('/discovery/clients');
}

export function addClient(name: string): Promise<Client> {
  return apiRequest<Client>('/discovery/clients', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export function fetchActivities(): Promise<Activity[]> {
  return apiRequest<Activity[]>('/discovery/activities');
}

export function createActivity(name: string): Promise<Activity> {
  return apiRequest<Activity>('/discovery/activities', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export function mergeActivity(activityId: string, intoId: string): Promise<Activity> {
  return apiRequest<Activity>(`/discovery/activities/${encodeURIComponent(activityId)}/merge`, {
    method: 'POST',
    body: JSON.stringify({ intoId }),
  });
}

export function retireActivity(activityId: string): Promise<Activity> {
  return apiRequest<Activity>(`/discovery/activities/${encodeURIComponent(activityId)}/retire`, {
    method: 'POST',
    body: '{}',
  });
}

export interface JudgedNearMiss {
  same: string;
  confidence: number;
  reason: string;
  modelId: string;
}

export async function askTheModelAboutNearMiss(
  typed: string,
  candidates: readonly string[],
): Promise<JudgedNearMiss | null> {
  const judged = await apiRequest<JudgedNearMiss | undefined>(
    '/node-pipeline/activities/near-miss',
    { method: 'POST', body: JSON.stringify({ typed, candidates }) },
  );

  return judged ?? null;
}
