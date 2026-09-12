import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  closeJob,
  fetchJobArtifacts,
  fetchJobGraph,
  fetchJobHeader,
  fetchMyTrack,
  fetchMyWorkCounts,
  forceCloseJob,
  searchWork,
  type ClientArtifact,
  type JobGraph,
  type JobHeader,
  type MyWorkCounts,
  type TrackLine,
  type WorkSearchResult,
} from '../api/jobGraphApi';

export const jobKeys = {
  graph: (jobId: string) => ['discovery', 'job', jobId, 'graph'] as const,
  header: (jobId: string) => ['discovery', 'job', jobId, 'header'] as const,
  artifacts: (jobId: string) => ['discovery', 'job', jobId, 'artifacts'] as const,

  myCounts: () => ['discovery', 'my-work-counts'] as const,
  myTrack: () => ['discovery', 'my-track'] as const,
};

export function useMyTrack(): UseQueryResult<TrackLine[]> {
  return useQuery({ queryKey: jobKeys.myTrack(), queryFn: fetchMyTrack });
}

export function useMyWorkCounts(): UseQueryResult<MyWorkCounts> {
  return useQuery({ queryKey: jobKeys.myCounts(), queryFn: fetchMyWorkCounts });
}

export function useJobGraph(jobId: string | undefined): UseQueryResult<JobGraph> {
  return useQuery({
    queryKey: jobKeys.graph(jobId ?? ''),
    queryFn: () => fetchJobGraph(jobId as string),
    enabled: jobId !== undefined,
  });
}

export function useJobHeader(jobId: string | undefined): UseQueryResult<JobHeader> {
  return useQuery({
    queryKey: jobKeys.header(jobId ?? ''),
    queryFn: () => fetchJobHeader(jobId as string),
    enabled: jobId !== undefined,
  });
}

export function useJobArtifacts(jobId: string | undefined): UseQueryResult<ClientArtifact[]> {
  return useQuery({
    queryKey: jobKeys.artifacts(jobId ?? ''),
    queryFn: () => fetchJobArtifacts(jobId as string),
    enabled: jobId !== undefined,
  });
}

export function useCloseJob(jobId: string): UseMutationResult<void, Error, { reason?: string }> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ reason }) =>
      reason === undefined ? closeJob(jobId) : forceCloseJob(jobId, reason),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: jobKeys.header(jobId) });
      void cache.invalidateQueries({ queryKey: jobKeys.graph(jobId) });
      void cache.invalidateQueries({ queryKey: ['discovery', 'rail'] });
    },
  });
}

export function useWorkSearch(term: string): UseQueryResult<WorkSearchResult> {
  const trimmed = term.trim();

  return useQuery({
    queryKey: ['discovery', 'search', trimmed],
    queryFn: () => searchWork(trimmed),
    enabled: trimmed.length >= 2,

    placeholderData: (previous) => previous,
  });
}
