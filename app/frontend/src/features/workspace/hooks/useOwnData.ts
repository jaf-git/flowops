import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  editOwnProfile,
  exportOwnData,
  fetchOwnData,
  type OwnData,
  type ProfileChange,
} from '../api/workspaceApi';

const OWN_DATA_QUERY_KEY = ['workspace', 'me'] as const;
const PEOPLE_QUERY_KEY = ['workspace', 'people'] as const;

export function useOwnData(): UseQueryResult<OwnData, Error> {
  return useQuery({ queryKey: OWN_DATA_QUERY_KEY, queryFn: fetchOwnData, retry: false });
}

export function useExportOwnData(): UseMutationResult<OwnData, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => exportOwnData(),
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: OWN_DATA_QUERY_KEY }),
  });
}

export function useEditOwnProfile(): UseMutationResult<ProfileChange, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (displayName: string) => editOwnProfile(displayName),
    retry: false,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: OWN_DATA_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: PEOPLE_QUERY_KEY });
    },
  });
}
