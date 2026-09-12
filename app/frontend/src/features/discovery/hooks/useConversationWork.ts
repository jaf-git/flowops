import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  closeBracket,
  declareWait,
  endWait,
  fetchConversationMarks,
  fetchConversationWaits,
  fetchConversationWork,
  handOverBracket,
  type CloseBracketRequest,
  type CloseBracketResponse,
  type ConversationBracket,
  type ConversationWait,
  type DeclareWaitRequest,
  type DeclareWaitResponse,
  type HandOverRequest,
  type HandOverResponse,
  type MessageMark,
} from '../api/bracketApi';

export const bracketKeys = {
  work: (conversationId: string) => ['discovery', 'brackets', conversationId] as const,
  waits: (conversationId: string) => ['discovery', 'waits', conversationId] as const,

  marks: (conversationId: string) => ['discovery', 'marks', conversationId] as const,
};

export function useConversationMarks(
  conversationId: string | undefined,
): UseQueryResult<MessageMark[]> {
  return useQuery({
    queryKey: bracketKeys.marks(conversationId ?? ''),
    queryFn: () => fetchConversationMarks(conversationId as string),
    enabled: conversationId !== undefined,
  });
}

export function useEndWait(
  conversationId: string,
): UseMutationResult<void, Error, { waitId: string; how: 'arrived' | 'withdraw' }> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ waitId, how }) => endWait(waitId, how),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
    },
  });
}

export function useConversationWork(
  conversationId: string | undefined,
): UseQueryResult<ConversationBracket[]> {
  return useQuery({
    queryKey: bracketKeys.work(conversationId ?? ''),
    queryFn: () => fetchConversationWork(conversationId as string),
    enabled: conversationId !== undefined,
  });
}

export function useConversationWaits(
  conversationId: string | undefined,
): UseQueryResult<ConversationWait[]> {
  return useQuery({
    queryKey: bracketKeys.waits(conversationId ?? ''),
    queryFn: () => fetchConversationWaits(conversationId as string),
    enabled: conversationId !== undefined,
  });
}

export function useCloseBracket(
  conversationId: string,
): UseMutationResult<
  CloseBracketResponse,
  Error,
  { bracketId: string; request: CloseBracketRequest }
> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ bracketId, request }) => closeBracket(bracketId, request),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
    },
  });
}

export function useHandOver(
  conversationId: string,
): UseMutationResult<
  HandOverResponse | undefined,
  Error,
  { bracketId: string; request: HandOverRequest }
> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ bracketId, request }) => handOverBracket(bracketId, request),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.marks(conversationId) });
    },
  });
}

export function useDeclareWait(
  conversationId: string,
): UseMutationResult<
  DeclareWaitResponse,
  Error,
  { bracketId: string; request: DeclareWaitRequest }
> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ bracketId, request }) => declareWait(bracketId, request),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: bracketKeys.work(conversationId) });
      void cache.invalidateQueries({ queryKey: bracketKeys.waits(conversationId) });
    },
  });
}
