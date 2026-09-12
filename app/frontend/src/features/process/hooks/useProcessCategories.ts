import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  createProcessCategory,
  deleteProcessCategory,
  fetchProcessCategories,
  fileRunUnder,
  archiveRun,
  restoreRun,
  type ProcessCategories,
} from '../api/processCategoryApi';

const CATEGORIES = ['process', 'categories'] as const;

export function useProcessCategories(): UseQueryResult<ProcessCategories, Error> {
  return useQuery({ queryKey: CATEGORIES, queryFn: fetchProcessCategories, retry: false });
}

export function useCreateProcessCategory(): UseMutationResult<{ id: string }, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => createProcessCategory(name),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES }),
  });
}

export function useDeleteProcessCategory(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteProcessCategory(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES }),
  });
}

export function useFileRunUnder(): UseMutationResult<
  void,
  Error,
  { instanceId: string; categoryId: string | null }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ instanceId, categoryId }) => fileRunUnder(instanceId, categoryId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES }),
  });
}

export function useArchiveRun(): UseMutationResult<
  void,
  Error,
  { instanceId: string; archived: boolean }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ instanceId, archived }) =>
      archived ? archiveRun(instanceId) : restoreRun(instanceId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['process-instances'] }),
  });
}
