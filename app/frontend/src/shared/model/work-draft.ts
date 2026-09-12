export type FieldSource = 'FROM_CONVERSATION' | 'FROM_HISTORY' | 'SUGGESTED';

export interface Sourced<T> {
  value: T;
  source: FieldSource;
}

export type WorkShape = 'TASK' | 'PROCESS';

export interface SuggestedStep {
  title: Sourced<string>;

  quotedFrom: string;

  description?: string | null;
  assigneeId: Sourced<string> | null;
  deadline: Sourced<string> | null;
}

export interface WorkDraft {
  shape: WorkShape | null;
  title: Sourced<string> | null;
  assigneeId: Sourced<string> | null;
  deadline: Sourced<string> | null;
  steps: SuggestedStep[];
}

export interface AgreedWork {
  shape: WorkShape;
  title: string;
  assigneeId: string;
  deadline: string | null;

  steps: { quotedFrom: string; title: string; assigneeId: string; deadline: string | null }[];
}
