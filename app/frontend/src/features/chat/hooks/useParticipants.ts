import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import { addParticipant, fetchParticipants, type Participant } from '../api/chatApi';

export const participantKeys = {
  of: (conversationId: string) => ['chat', 'participants', conversationId] as const,
};

export function useParticipants(conversationId: string | undefined): UseQueryResult<Participant[]> {
  return useQuery({
    queryKey: participantKeys.of(conversationId ?? ''),
    queryFn: () => fetchParticipants(conversationId as string),
    enabled: conversationId !== undefined,
  });
}

export function useBringIntoRoom(conversationId: string): UseMutationResult<void, Error, string> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: (personId: string) => addParticipant(conversationId, personId),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: participantKeys.of(conversationId) });
      void cache.invalidateQueries({ queryKey: ['chat', 'conversation', conversationId] });
    },
  });
}
