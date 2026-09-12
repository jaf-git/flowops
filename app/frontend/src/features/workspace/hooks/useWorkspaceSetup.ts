import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  fetchSetupPrefill,
  setUpWorkspace,
  type SetupInput,
  type SetupPrefill,
  type WorkspaceSetup,
} from '../api/workspaceApi';

const SETUP_PREFILL_KEY = ['workspace', 'setup', 'prefill'] as const;
const SESSION_QUERY_KEY = ['auth', 'session'] as const;

export function useSetupPrefill(): UseQueryResult<SetupPrefill, Error> {
  return useQuery({
    queryKey: SETUP_PREFILL_KEY,
    queryFn: fetchSetupPrefill,
    retry: false,
  });
}

export function useSetUpWorkspace(): UseMutationResult<WorkspaceSetup, Error, SetupInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SetupInput) => setUpWorkspace(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY }),
  });
}
