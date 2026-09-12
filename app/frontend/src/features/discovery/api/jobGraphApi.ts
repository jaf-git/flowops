import { apiRequest } from '../../../shared/api/client';

import type { ClientArtifact, JobGraph, JobHeader, MyWorkCounts, TrackLine } from '../model/graph';

export * from '../model/graph';

export function fetchJobGraph(jobId: string): Promise<JobGraph> {
  return apiRequest<JobGraph>(`/discovery/graph/job/${encodeURIComponent(jobId)}`);
}

export function fetchMyWorkCounts(): Promise<MyWorkCounts> {
  return apiRequest<MyWorkCounts>('/discovery/graph/my-work-counts');
}

export function fetchMyTrack(): Promise<TrackLine[]> {
  return apiRequest<TrackLine[]>('/discovery/graph/my-track');
}

export function fetchJobHeader(jobId: string): Promise<JobHeader> {
  return apiRequest<JobHeader>(`/discovery/graph/job/${encodeURIComponent(jobId)}/header`);
}

export function fetchJobArtifacts(jobId: string): Promise<ClientArtifact[]> {
  return apiRequest<ClientArtifact[]>(
    `/discovery/graph/job/${encodeURIComponent(jobId)}/artifacts`,
  );
}

export function closeJob(jobId: string): Promise<void> {
  return apiRequest<void>(`/discovery/brackets/job/${encodeURIComponent(jobId)}/close`, {
    method: 'POST',
    body: '{}',
  });
}

export function forceCloseJob(jobId: string, reason: string): Promise<void> {
  return apiRequest<void>(`/discovery/brackets/job/${encodeURIComponent(jobId)}/force-close`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export interface WorkMatch {
  nodeId: string;
  bracketId: string;
  jobId: string;
  conversationId: string;
  workType: string;
  text: string;
}

export interface WorkSearchResult {
  matches: WorkMatch[];

  beyondReach: number;
}

export function searchWork(query: string): Promise<WorkSearchResult> {
  return apiRequest<WorkSearchResult>(`/discovery/graph/search?q=${encodeURIComponent(query)}`);
}
