import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  fetchWorkspaceSettings,
  updateWorkspaceSettings,
  type WorkspaceSettings,
  type WorkspaceSettingsInput,
} from '../api/workspaceApi';

const SETTINGS_QUERY_KEY = ['workspace', 'settings'] as const;

export function useWorkspaceSettings(): UseQueryResult<WorkspaceSettings, Error> {
  return useQuery({
    queryKey: SETTINGS_QUERY_KEY,
    queryFn: fetchWorkspaceSettings,
    retry: false,
  });
}

export function useUpdateWorkspaceSettings(): UseMutationResult<
  WorkspaceSettings,
  Error,
  WorkspaceSettingsInput
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: WorkspaceSettingsInput) => updateWorkspaceSettings(input),
    retry: false,
    onSuccess: (saved) => queryClient.setQueryData(SETTINGS_QUERY_KEY, saved),
  });
}
