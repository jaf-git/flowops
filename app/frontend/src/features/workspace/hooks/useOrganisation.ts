import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  assignFunctionalRole,
  createDepartment,
  createFunctionalRole,
  deleteDepartment,
  deleteFunctionalRole,
  fetchOrganisation,
  fetchRoleAssignments,
  renameDepartment,
  renameFunctionalRole,
  type FunctionalRoleRow,
  type Organisation,
} from '../api/organisationApi';

const ORGANISATION_KEY = ['workspace', 'organisation'] as const;
const ASSIGNMENTS_KEY = ['workspace', 'organisation', 'assignments'] as const;

export function useOrganisation(): UseQueryResult<Organisation> {
  return useQuery({ queryKey: ORGANISATION_KEY, queryFn: fetchOrganisation, retry: false });
}

export function useRoleAssignments(): UseQueryResult<Record<string, string>> {
  return useQuery({ queryKey: ASSIGNMENTS_KEY, queryFn: fetchRoleAssignments, retry: false });
}

export function useCreateDepartment(): UseMutationResult<string, Error, string> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: createDepartment,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ORGANISATION_KEY }),
  });
}

export function useRenameDepartment(): UseMutationResult<
  void,
  Error,
  { id: string; name: string }
> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: renameDepartment,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ORGANISATION_KEY }),
  });
}

export function useDeleteDepartment(): UseMutationResult<void, Error, string> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: deleteDepartment,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ORGANISATION_KEY }),
  });
}

export function useCreateFunctionalRole(): UseMutationResult<
  FunctionalRoleRow,
  Error,
  { name: string; departmentId: string }
> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: createFunctionalRole,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ORGANISATION_KEY }),
  });
}

export function useRenameFunctionalRole(): UseMutationResult<
  void,
  Error,
  { id: string; name: string }
> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: renameFunctionalRole,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ORGANISATION_KEY }),
  });
}

export function useDeleteFunctionalRole(): UseMutationResult<void, Error, string> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: deleteFunctionalRole,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ORGANISATION_KEY }),
  });
}

export function useAssignFunctionalRole(): UseMutationResult<
  void,
  Error,
  { membershipId: string; functionalRoleId: string | null }
> {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: assignFunctionalRole,
    onSuccess: () => void cache.invalidateQueries({ queryKey: ASSIGNMENTS_KEY }),
  });
}
