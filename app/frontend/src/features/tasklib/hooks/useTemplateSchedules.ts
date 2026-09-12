import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  fetchTemplateSchedules,
  pauseTemplateSchedule,
  resumeTemplateSchedule,
  setTemplateSchedule,
  type ScheduleInput,
  type TemplateSchedule,
} from '../api/templateScheduleApi';

const SCHEDULES_KEY = ['task-templates', 'schedules'] as const;

export function useTemplateSchedules(
  templateId: string,
): UseQueryResult<TemplateSchedule[], Error> {
  return useQuery({
    queryKey: [...SCHEDULES_KEY, templateId],
    queryFn: () => fetchTemplateSchedules(templateId),
    retry: false,
  });
}

export function useSetSchedule(
  templateId: string,
): UseMutationResult<TemplateSchedule, Error, ScheduleInput> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: (schedule: ScheduleInput) => setTemplateSchedule({ templateId, schedule }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: [...SCHEDULES_KEY, templateId] });
    },
  });
}

export function useSchedulePause(
  templateId: string,
): UseMutationResult<TemplateSchedule, Error, { scheduleId: string; running: boolean }> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ scheduleId, running }: { scheduleId: string; running: boolean }) =>
      running
        ? pauseTemplateSchedule({ templateId, scheduleId })
        : resumeTemplateSchedule({ templateId, scheduleId }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: [...SCHEDULES_KEY, templateId] });
    },
  });
}
