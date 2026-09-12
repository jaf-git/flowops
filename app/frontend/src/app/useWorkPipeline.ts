import { useQuery } from '@tanstack/react-query';

import { stagesOf, type WorkStage } from '../shared/model/workPipeline';
import { useWorkVocabulary } from '../shared/model/workVocabulary';

export interface WorkPipelineSubject {
  taskId: string;
  taskTitle: string;
  taskState: string;

  templateId: string | null;
}

export interface WorkPipelineReading {
  stages: readonly WorkStage[];

  isPending: boolean;
}

export function useWorkPipeline(subject: WorkPipelineSubject): WorkPipelineReading {
  const vocabulary = useWorkVocabulary();
  const { taskId, taskTitle, taskState, templateId } = subject;

  const origin = useQuery({
    queryKey: ['work-pipeline', 'origin', taskId],
    queryFn: () => vocabulary.originOf(taskId),
    retry: false,

    // Whether a task came from a message is settled when the task is created and never
    // changes after. Without this the answer is re-asked on every remount, and for the
    // ordinary case -- a task that came from no message -- that is a 404 each time.
    staleTime: Infinity,
  });

  const analysis = useQuery({
    queryKey: ['work-pipeline', 'analysis', templateId],
    queryFn: () => vocabulary.analysisOf(templateId as string),
    enabled: templateId !== null,
    retry: false,
  });

  const lineage = useQuery({
    queryKey: ['work-pipeline', 'lineage', templateId],
    queryFn: () => vocabulary.lineageOf(templateId as string),
    enabled: templateId !== null,
    retry: false,
  });

  const entry = useQuery({
    queryKey: ['work-pipeline', 'entry', templateId],
    queryFn: () => vocabulary.entryOf(templateId as string),
    enabled: templateId !== null,
    retry: false,
  });

  const observations = useQuery({
    queryKey: ['work-pipeline', 'observations', templateId],
    queryFn: () => vocabulary.observationsOn('TASK_TEMPLATE', templateId as string),
    enabled: templateId !== null,
    retry: false,
  });

  return {
    stages: stagesOf({
      taskId,
      taskTitle,
      taskState,
      templateId,
      templateTitle: entry.data?.title ?? null,
      templateIsDraft: entry.data?.draft ?? false,
      origin: origin.data ?? null,
      analysis: analysis.data ?? null,
      lineage: lineage.data ?? null,
      observations: observations.data ?? [],
    }),

    isPending:
      origin.isFetching ||
      analysis.isFetching ||
      lineage.isFetching ||
      entry.isFetching ||
      observations.isFetching,
  };
}
