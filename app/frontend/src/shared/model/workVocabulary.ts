import { createContext, useContext } from 'react';

export interface WorkEntry {
  id: string;
  title: string;
  timesUsed: number;
}

export interface WorkPlannedIn {
  templateId: string;
  name: string;
  position: number;
  active: boolean;
}

export interface WorkRun {
  instanceId: string;
  instanceName: string;
  instanceState: 'RUNNING' | 'COMPLETE' | 'ABANDONED';
  stepId: string;
  stepCondition: 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';
  taskId: string | null;
  startedAt: string;
}

export interface WorkLineage {
  processTemplates: WorkPlannedIn[];
  runs: WorkRun[];
  runsTotal: number;
}

export interface WorkVocabulary {
  listApproved: () => Promise<readonly WorkEntry[]>;

  lineageOf: (taskTemplateId: string) => Promise<WorkLineage>;

  originOf: (taskId: string) => Promise<WorkOrigin | null>;

  analysisOf: (taskTemplateId: string) => Promise<WorkAnalysis>;

  observationsOn: (subject: WorkSubject, id: string) => Promise<readonly WorkObservation[]>;

  entryOf: (taskTemplateId: string) => Promise<WorkEntryState>;
}

export interface WorkEntryState {
  title: string;
  draft: boolean;
}

export type WorkSubject = 'TASK_TEMPLATE' | 'PROCESS_TEMPLATE';

export interface WorkOrigin {
  conversationId: string;
  messageId: string;
}

export interface WorkAnalysis {
  stamped: number;

  medianActiveSeconds: number | null;

  passedFirstTime: number;
  reviewed: number;
}

export interface WorkObservation {
  kind: string;

  findingKey: string;
}

export const WorkVocabularyContext = createContext<WorkVocabulary | null>(null);

export function useWorkVocabulary(): WorkVocabulary {
  const vocabulary = useContext(WorkVocabularyContext);
  if (vocabulary === null) {
    throw new Error(
      'useWorkVocabulary was called outside WorkVocabularyProvider — the composition root must provide it',
    );
  }
  return vocabulary;
}
