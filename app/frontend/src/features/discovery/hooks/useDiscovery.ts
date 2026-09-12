import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  answerNudge,
  blockNode,
  deleteNode,
  endThread,
  enrichNode,
  fetchJobsForConversation,
  fetchNodeTrail,
  fetchNudge,
  markMessage,
  openJob,
  recordOutput,
  relinkNode,
  resumeNode,
  type EnrichNodeRequest,
  type JobOption,
  type MarkedResponse,
  type MarkMessageRequest,
  type NodeEnrichment,
  type NodeProgress,
  type NodeTrail,
  type NudgeAnswer,
  type NudgeSubject,
  type OpenJobRequest,
  type OutputType,
  type TrackEnding,
  type WaitingOn,
} from '../api/discoveryApi';

export const discoveryKeys = {
  all: ['discovery'] as const,

  canvasRoot: ['discovery', 'canvas'] as const,

  canvas: (jobId?: string) => ['discovery', 'canvas', jobId ?? 'all'] as const,

  job: (jobId: string) => ['discovery', 'job', jobId] as const,

  track: (trackId: string) => ['discovery', 'track', trackId] as const,

  enrichment: (nodeId: string) => ['discovery', 'enrichment', nodeId] as const,

  trail: (nodeId: string) => ['discovery', 'trail', nodeId] as const,

  subject: (messageId: string) => ['discovery', 'subject', messageId] as const,

  jobsFor: (conversationId: string) => ['discovery', 'jobs', conversationId] as const,

  nudge: () => ['discovery', 'nudge'] as const,

  digest: () => ['discovery', 'digest'] as const,

  orphans: () => ['discovery', 'orphans'] as const,
} as const;

export function useOpenJob(): UseMutationResult<MarkedResponse, unknown, OpenJobRequest> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: OpenJobRequest) => openJob(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.all });
    },
  });
}

export function useMarkMessage(): UseMutationResult<MarkedResponse, unknown, MarkMessageRequest> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: MarkMessageRequest) => markMessage(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.canvasRoot });
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.orphans() });
    },
  });
}

export function useJobsForConversation(
  conversationId: string | undefined,

  describing = false,
): UseQueryResult<JobOption[]> {
  return useQuery({
    queryKey: [...discoveryKeys.jobsFor(conversationId ?? 'workspace'), describing],
    queryFn: () => fetchJobsForConversation(conversationId, describing),

    retry: false,
  });
}

export function useUndoMark(): UseMutationResult<void, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (nodeId: string) => deleteNode(nodeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.all });
    },
  });
}

export function useRelinkNode(): UseMutationResult<
  MarkedResponse,
  unknown,
  { nodeId: string; jobId: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ nodeId, jobId }: { nodeId: string; jobId: string }) => relinkNode(nodeId, jobId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.all });
    },
  });
}

function refreshWorkAndNudge(queryClient: ReturnType<typeof useQueryClient>): void {
  void queryClient.invalidateQueries({ queryKey: discoveryKeys.canvasRoot });
  void queryClient.invalidateQueries({ queryKey: discoveryKeys.nudge() });
}

export function useRecordOutput(): UseMutationResult<
  NodeProgress,
  unknown,
  { nodeId: string; outputType: OutputType }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ nodeId, outputType }: { nodeId: string; outputType: OutputType }) =>
      recordOutput(nodeId, outputType),
    onSuccess: () => refreshWorkAndNudge(queryClient),
  });
}

export function useBlockNode(): UseMutationResult<
  NodeProgress,
  unknown,
  { nodeId: string; waitingOn: WaitingOn }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ nodeId, waitingOn }: { nodeId: string; waitingOn: WaitingOn }) =>
      blockNode(nodeId, waitingOn),
    onSuccess: () => refreshWorkAndNudge(queryClient),
  });
}

export function useResumeNode(): UseMutationResult<NodeProgress, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (nodeId: string) => resumeNode(nodeId),
    onSuccess: () => refreshWorkAndNudge(queryClient),
  });
}

export function useNudge(enabled = true): UseQueryResult<NudgeSubject | null> {
  return useQuery({
    queryKey: discoveryKeys.nudge(),
    queryFn: fetchNudge,
    enabled,
    retry: false,
  });
}

export function useAnswerNudge(): UseMutationResult<
  NodeProgress,
  unknown,
  { nodeId: string; answer: NudgeAnswer }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ nodeId, answer }: { nodeId: string; answer: NudgeAnswer }) =>
      answerNudge(nodeId, answer),
    onSuccess: () => refreshWorkAndNudge(queryClient),
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.nudge() });
    },
  });
}

export function useEndThread(): UseMutationResult<TrackEnding, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (trackId: string) => endThread(trackId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.all });
    },
  });
}

export function useEnrichNode(): UseMutationResult<
  NodeEnrichment,
  unknown,
  { nodeId: string; fields: EnrichNodeRequest }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ nodeId, fields }: { nodeId: string; fields: EnrichNodeRequest }) =>
      enrichNode(nodeId, fields),
    onSuccess: (_enriched, { nodeId }) => {
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.canvasRoot });
      void queryClient.invalidateQueries({ queryKey: discoveryKeys.enrichment(nodeId) });
    },
  });
}

export function useNodeTrail(nodeId: string | null): UseQueryResult<NodeTrail> {
  return useQuery({
    queryKey: discoveryKeys.trail(nodeId ?? 'none'),
    queryFn: () => fetchNodeTrail(nodeId ?? ''),
    enabled: nodeId !== null,
  });
}
