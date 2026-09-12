export type BracketState = 'OPEN' | 'WAITING' | 'CLOSED' | 'LAPSED';

export type CloseKind =
  | 'DELIVERED'
  | 'DONE'
  | 'DROPPED'
  | 'LAPSED'
  | 'PARENT_CLOSED'
  | 'OVERRIDE'
  | 'HANDED_OVER'
  | 'CADENCE_CLOSED'
  | 'MERGED';

export type WaitKind = 'CLIENT' | 'SUPPLIER' | 'COLLEAGUE' | 'APPROVAL';

export type OutputKind = 'TEXT' | 'LINK' | 'MESSAGE_REF';

export interface ConversationBracket {
  bracketId: string;

  address: string;
  workType: string;
  state: BracketState;
  closeKind: CloseKind | null;
  outputValue: string | null;

  outputKind: OutputKind | null;
  performerId: string | null;
  performerName: string | null;
  messageIds: string[];
  openedAt: string;
  lastActivityAt: string;

  nudged: boolean;

  openWaits: number;
  live: boolean;
}

export interface ConversationWait {
  waitId: string;
  bracketId: string;
  kind: WaitKind;

  external: boolean;
  reason: string | null;
  expectedBy: string | null;
  openedAt: string;

  blockingAddress: string | null;
}

export interface MessageMark {
  messageId: string;
  nodeId: string;
  bracketId: string | null;
  workType: string;

  activity: string | null;

  address: string;

  project: string | null;

  client: string | null;

  title: string | null;
  state: BracketState | 'UNPLACED';
  closeKind: CloseKind | null;
  jobId: string;
  jobName: string;

  performerId: string | null;
  performerName: string | null;

  boundary: boolean;
}

export interface Client {
  id: string;
  name: string;
  kind: 'CLIENT' | 'INTERNAL' | 'SUPPLIER' | 'PROSPECT' | 'UNCLASSIFIED';
  classified: boolean;
}

export type ActivityStatus = 'ACTIVE' | 'MERGED' | 'RETIRED';

export interface Activity {
  id: string;
  name: string;
  slug: string;

  status: ActivityStatus;

  timesUsed: number;
  lastUsedAt: string | null;

  departments: string[];

  counterparties: string[];

  tooGenericToBeOneThing: boolean;
}

export function describeEnding(kind: CloseKind): { label: string; completed: boolean } {
  switch (kind) {
    case 'DELIVERED':
      return { label: 'Delivered', completed: true };
    case 'DONE':
      return { label: 'Done', completed: true };
    case 'DROPPED':
      return { label: 'Dropped', completed: false };
    case 'LAPSED':
      return { label: 'Lapsed', completed: false };
    case 'PARENT_CLOSED':
      return { label: 'Closed with its parent', completed: false };
    case 'OVERRIDE':
      return { label: 'Force-closed', completed: false };
    case 'HANDED_OVER':
      return { label: 'Handed over', completed: false };
    case 'CADENCE_CLOSED':
      return { label: 'Closed on cadence', completed: false };
    case 'MERGED':
      return { label: 'Merged', completed: false };
  }
}
