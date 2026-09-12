import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  createTaskCategory,
  deleteTaskCategory,
  fetchTaskCategories,
  fileTaskUnder,
  renameTaskCategory,
  type TaskCategories,
} from '../api/taskCategoryApi';

const CATEGORIES = ['task', 'categories'] as const;

export function useTaskCategories(): UseQueryResult<TaskCategories, Error> {
  return useQuery({ queryKey: CATEGORIES, queryFn: fetchTaskCategories, retry: false });
}

export function useCreateTaskCategory(): UseMutationResult<{ id: string }, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => createTaskCategory(name),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES }),
  });
}

export function useRenameTaskCategory(): UseMutationResult<
  void,
  Error,
  { id: string; name: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }) => renameTaskCategory(id, name),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES }),
  });
}

export function useDeleteTaskCategory(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteTaskCategory(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES }),
  });
}

export function useFileTaskUnder(): UseMutationResult<
  void,
  Error,
  { taskId: string; categoryId: string | null }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, categoryId }) => fileTaskUnder(taskId, categoryId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CATEGORIES });
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}
