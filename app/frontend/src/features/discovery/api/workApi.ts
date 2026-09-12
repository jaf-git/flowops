import { apiRequest } from '../../../shared/api/client';

export type MarkVerb = 'CREATE' | 'ADD' | 'JOIN';

export interface OpenWork {
  bracketId: string;
  jobId: string;
  workType: string;

  destination: string;
  performerId: string | null;
  performerName: string | null;
}

export interface EarlierWorkHere {
  workType: string;

  people: string[];
  activities: string[];
}

export interface VerbOffer {
  describe: string;

  joins: boolean;
  bracketId: string | null;
  workType: string;
  verbs: MarkVerb[];

  othersHere: OpenWork[];

  clientName: string | null;

  earlierWorkHere: EarlierWorkHere | null;
}

export interface MarkWorkRequest {
  messageId: string;
  jobId: string;

  performerId: string | null;
  workType?: string;
  verb: MarkVerb;

  activityId?: string;

  joining?: string;
}

export interface MarkedWork {
  nodeId: string;
  bracketId: string;

  joined: boolean;

  destination: string;
  workType: string;

  activity: string | null;
  joinedWith: string | null;
}

export interface Closable {
  bracketId: string;
  jobId: string;
  workType: string;
  destination: string;
  performerId: string | null;
  performerName: string | null;
  closureRight: string | null;
  holderName: string | null;

  waiting: number;
  waitingHolderNames: string[];
}

export interface DeliverableHere {
  mine: Closable[];

  heldByOthers: string[];
}

export interface Delivered {
  bracketId: string;
  destination: string;

  output: string;
  released: number;
  unblocked: string[];
}

export function fetchVerbOffer(
  jobId: string,
  conversationId: string,
  performerId: string | null,
  workType: string | null,
): Promise<VerbOffer> {
  const query = new URLSearchParams({ jobId, conversationId });

  if (performerId !== null) {
    query.set('performerId', performerId);
  }

  if (workType !== null) {
    query.set('workType', workType);
  }

  return apiRequest<VerbOffer>(`/discovery/brackets/preview?${query.toString()}`);
}

export function markWork(request: MarkWorkRequest): Promise<MarkedWork> {
  return apiRequest<MarkedWork>('/discovery/work', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function fetchDeliverableHere(messageId: string): Promise<DeliverableHere> {
  return apiRequest<DeliverableHere>(
    `/discovery/work/${encodeURIComponent(messageId)}/deliverable`,
  );
}

export function deliverWork(messageId: string, bracketId?: string): Promise<Delivered> {
  return apiRequest<Delivered>(`/discovery/work/${encodeURIComponent(messageId)}/deliver`, {
    method: 'POST',
    body: JSON.stringify(bracketId === undefined ? {} : { bracketId }),
  });
}
