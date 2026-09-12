import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query';

import { ApiError, apiRequest } from '../../../shared/api/client';
import { startProcessFromDescriptions, type AgreedWork } from '../api/chatAssistApi';

export function useCreateAgreedWork(): UseMutationResult<void, ApiError, AgreedWork> {
  const queries = useQueryClient();

  return useMutation<void, ApiError, AgreedWork>({
    mutationFn: async (agreed) => {
      if (agreed.shape === 'PROCESS') {
        await startProcessFromDescriptions(
          agreed.title,
          agreed.assigneeId,
          agreed.steps.map((step) => ({
            title: step.title,
            description: null,
            assigneeId: step.assigneeId,
            deadline: step.deadline,
            priority: 'NORMAL',
          })),
        );
        return;
      }

      const task = await apiRequest<{ id: string }>('/tasks', {
        method: 'POST',
        body: JSON.stringify({
          title: agreed.title,
          description: null,
          assigneeId: agreed.assigneeId,
          deadline: agreed.deadline,
          priority: 'NORMAL',
          kind: 'TASK',
        }),
      });

      for (const item of agreed.steps) {
        await apiRequest<unknown>(`/tasks/${task.id}/checklist`, {
          method: 'POST',
          body: JSON.stringify({ text: item.title }),
        });
      }
    },
    onSuccess: () => {
      void queries.invalidateQueries();
    },
  });
}
