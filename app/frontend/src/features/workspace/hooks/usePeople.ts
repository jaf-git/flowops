import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  deactivatePerson,
  erasePerson,
  fetchErasurePreview,
  fetchPeople,
  fetchReassignPreview,
  reassignReportingLine,
  invitePerson,
  revokeInvitation,
  type Deactivation,
  type Erasure,
  type ErasurePreview,
  type Invitation,
  type InviteInput,
  type People,
  type ReassignedReportingLine,
  type ReassignPreview,
  type RevokedInvitation,
} from '../api/workspaceApi';

const PEOPLE_QUERY_KEY = ['workspace', 'people'] as const;

export function usePeople(): UseQueryResult<People, Error> {
  return useQuery({ queryKey: PEOPLE_QUERY_KEY, queryFn: fetchPeople, retry: false });
}

export interface Colleague {
  id: string;
  displayName: string;
}

export function useActiveColleagues(): Colleague[] {
  const directory = usePeople();
  return (directory.data?.people ?? [])
    .filter((person) => person.status === 'ACTIVE')
    .map((person) => ({ id: person.personId, displayName: person.displayName }));
}

export function useInvitePerson(): UseMutationResult<Invitation, Error, InviteInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: InviteInput) => invitePerson(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PEOPLE_QUERY_KEY }),
  });
}

export function useRevokeInvitation(): UseMutationResult<RevokedInvitation, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => revokeInvitation(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PEOPLE_QUERY_KEY }),
  });
}

export function useReassignPreview(
  membershipId: string | undefined,
  proposedManagerId: string | undefined,
): UseQueryResult<ReassignPreview, Error> {
  return useQuery({
    queryKey: ['workspace', 'people', membershipId, 'manager', 'preview', proposedManagerId],
    queryFn: () => fetchReassignPreview(membershipId ?? '', proposedManagerId ?? ''),
    enabled: membershipId !== undefined && proposedManagerId !== undefined,
    retry: false,
  });
}

export function useReassignReportingLine(): UseMutationResult<
  ReassignedReportingLine,
  Error,
  { membershipId: string; proposedManagerId: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { membershipId: string; proposedManagerId: string }) =>
      reassignReportingLine(input.membershipId, input.proposedManagerId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PEOPLE_QUERY_KEY }),
  });
}

export function useDeactivatePerson(): UseMutationResult<Deactivation, Error, string> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deactivatePerson,
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PEOPLE_QUERY_KEY }),
  });
}

export function useErasurePreview(
  membershipId: string | undefined,
): UseQueryResult<ErasurePreview, Error> {
  return useQuery({
    queryKey: ['workspace', 'people', membershipId, 'erasure'],
    queryFn: () => fetchErasurePreview(membershipId ?? ''),
    enabled: membershipId !== undefined,
    retry: false,
  });
}

export function useErasePerson(): UseMutationResult<
  Erasure,
  Error,
  { membershipId: string; typedName: string }
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: { membershipId: string; typedName: string }) =>
      erasePerson(input.membershipId, input.typedName),
    retry: false,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PEOPLE_QUERY_KEY }),
  });
}
