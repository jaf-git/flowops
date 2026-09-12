import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  deliverWork,
  fetchDeliverableHere,
  fetchVerbOffer,
  markWork,
  type Delivered,
  type DeliverableHere,
  type MarkWorkRequest,
  type MarkedWork,
  type VerbOffer,
} from '../api/workApi';

import { deleteNode } from '../api/discoveryApi';
import { bracketKeys } from './useConversationWork';
import { jobKeys } from './useJobGraph';

export const workKeys = {
  offers: ['discovery', 'work-offer'] as const,
  offer: (jobId: string, conversationId: string, performerId: string, workType: string) =>
    ['discovery', 'work-offer', jobId, conversationId, performerId, workType] as const,
  deliverable: (messageId: string) => ['discovery', 'deliverable', messageId] as const,
};

export function useVerbOffer(
  jobId: string | undefined,
  conversationId: string,
  performerId: string | null,
  workType: string | null,
): UseQueryResult<VerbOffer> {
  return useQuery({
    queryKey: workKeys.offer(
      jobId ?? '',
      conversationId,
      performerId ?? 'unclaimed',
      workType ?? 'derived',
    ),
    enabled: jobId !== undefined,
    queryFn: () => fetchVerbOffer(jobId as string, conversationId, performerId, workType),

    placeholderData: (previous) => previous,
  });
}

export function useMarkWork(
  conversationId: string,
): UseMutationResult<MarkedWork, Error, MarkWorkRequest> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: (request: MarkWorkRequest) => markWork(request),
    onSuccess: (_marked, request) => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.marks(conversationId) });
      void cache.invalidateQueries({ queryKey: workKeys.offers });
      void cache.invalidateQueries({ queryKey: jobKeys.header(request.jobId) });
    },
  });
}

export function useTakeMarkBack(conversationId: string): UseMutationResult<void, Error, TakeBack> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: (taken: TakeBack) => deleteNode(taken.nodeId),
    onSuccess: (_nothing, taken) => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.marks(conversationId) });
      void cache.invalidateQueries({ queryKey: workKeys.offers });
      void cache.invalidateQueries({ queryKey: jobKeys.header(taken.jobId) });
    },
  });
}

export interface TakeBack {
  nodeId: string;
  jobId: string;
}

export function useDeliverableHere(
  messageId: string,
  asked: boolean,
): UseQueryResult<DeliverableHere> {
  return useQuery({
    queryKey: workKeys.deliverable(messageId),
    enabled: asked,
    queryFn: () => fetchDeliverableHere(messageId),
  });
}

export function useDeliverWork(
  conversationId: string,
): UseMutationResult<Delivered, Error, { messageId: string; bracketId?: string; jobId: string }> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ messageId, bracketId }) => deliverWork(messageId, bracketId),
    onSuccess: (_delivered, { messageId, jobId }) => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.marks(conversationId) });
      void cache.invalidateQueries({ queryKey: workKeys.deliverable(messageId) });
      void cache.invalidateQueries({ queryKey: workKeys.offers });
      void cache.invalidateQueries({ queryKey: jobKeys.header(jobId) });
    },
  });
}
