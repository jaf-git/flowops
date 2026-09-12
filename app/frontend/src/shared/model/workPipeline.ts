import type { WorkAnalysis, WorkLineage, WorkObservation, WorkOrigin } from './workVocabulary';

export type WorkStageKind =
  'MESSAGE' | 'TASK' | 'TEMPLATE' | 'ANALYSIS' | 'PLANNED_IN' | 'OBSERVATIONS';

export type WorkBirth = 'TYPED' | 'STAMPED' | 'FROM_MESSAGE';

export interface WorkPipelineInput {
  taskId: string;
  taskTitle: string;
  taskState: string;

  templateId: string | null;
  templateTitle: string | null;

  templateIsDraft: boolean;
  origin: WorkOrigin | null;
  analysis: WorkAnalysis | null;
  lineage: WorkLineage | null;
  observations: readonly WorkObservation[];
}

export interface WorkStage {
  kind: WorkStageKind;

  subjectId: string;

  facts: Readonly<Record<string, string | number | null>>;
}

export function birthOf(input: WorkPipelineInput): WorkBirth {
  if (input.origin !== null) {
    return 'FROM_MESSAGE';
  }

  return input.templateId !== null && !input.templateIsDraft ? 'STAMPED' : 'TYPED';
}

export function stagesOf(input: WorkPipelineInput): readonly WorkStage[] {
  const stages: WorkStage[] = [];

  if (input.origin !== null) {
    stages.push({
      kind: 'MESSAGE',
      subjectId: input.origin.messageId,
      facts: { conversationId: input.origin.conversationId, messageId: input.origin.messageId },
    });
  }

  stages.push({
    kind: 'TASK',
    subjectId: input.taskId,
    facts: { title: input.taskTitle, state: input.taskState, birth: birthOf(input) },
  });

  if (input.templateId !== null) {
    stages.push({
      kind: 'TEMPLATE',
      subjectId: input.templateId,
      facts: { title: input.templateTitle, draft: input.templateIsDraft ? 'DRAFT' : 'APPROVED' },
    });
  }

  if (input.templateId !== null && input.analysis !== null && input.analysis.stamped > 1) {
    stages.push({
      kind: 'ANALYSIS',
      subjectId: input.templateId,
      facts: {
        stamped: input.analysis.stamped,
        medianActiveSeconds: input.analysis.medianActiveSeconds,
        passedFirstTime: input.analysis.passedFirstTime,
        reviewed: input.analysis.reviewed,
      },
    });
  }

  if (
    input.templateId !== null &&
    input.lineage !== null &&
    input.lineage.processTemplates.length > 0
  ) {
    stages.push({
      kind: 'PLANNED_IN',
      subjectId: input.templateId,
      facts: {
        processes: input.lineage.processTemplates.length,
        firstProcess: input.lineage.processTemplates[0]?.name ?? null,
        runs: input.lineage.runsTotal,
      },
    });
  }

  if (input.observations.length > 0) {
    stages.push({
      kind: 'OBSERVATIONS',
      subjectId: input.templateId ?? input.taskId,
      facts: { count: input.observations.length, first: input.observations[0]?.kind ?? null },
    });
  }

  return stages;
}
