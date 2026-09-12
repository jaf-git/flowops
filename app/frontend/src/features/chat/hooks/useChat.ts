import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  appendMessagesToTemplate,
  assignTaskInConversation,
  convertMessage,
  createGroup,
  draftProcessFromMessages,
  fetchAssignmentContext,
  fetchConversations,
  fetchConversionContext,
  fetchMessages,
  fetchTaskOrigin,
  joinRoom,
  leaveRoom,
  markRead,
  renameRoom,
  sendMessage,
  startConversation,
  startProcessFromMessages,
  startRunInConversation,
  type AssignmentContext,
  type ConversationRow,
  type ConversationsPayload,
  type ConversionContext,
  type ConversionDraft,
  type DirectTaskDraft,
  type MessageSelectionDraft,
  type SubmittedProcessStep,
  type MessagesPayload,
  type TaskOrigin,
} from '../api/chatApi';

export const CONVERSATIONS_QUERY_KEY = ['chat', 'conversations'] as const;

const messagesKey = (conversationId: string): readonly unknown[] => [
  'chat',
  'conversation',
  conversationId,
];

const THREAD_POLL_MS = 5_000;

const RAIL_POLL_MS = 15_000;

export function useConversations(): UseQueryResult<ConversationsPayload> {
  return useQuery({
    queryKey: CONVERSATIONS_QUERY_KEY,
    queryFn: fetchConversations,
    refetchInterval: RAIL_POLL_MS,
    retry: false,
  });
}

export function useMessages(conversationId: string | undefined): UseQueryResult<MessagesPayload> {
  return useQuery({
    queryKey: messagesKey(conversationId ?? 'none'),
    queryFn: () => fetchMessages(conversationId as string),
    enabled: conversationId !== undefined,
    refetchInterval: THREAD_POLL_MS,
    retry: false,
  });
}

export function useSendMessage(
  conversationId: string,
): UseMutationResult<unknown, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: string) => sendMessage(conversationId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: messagesKey(conversationId) });
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useConversionContext(
  conversationId: string | undefined,
  messageId: string | undefined,
): UseQueryResult<ConversionContext> {
  return useQuery({
    queryKey: ['chat', 'conversion', conversationId ?? 'none', messageId ?? 'none'],
    queryFn: () => fetchConversionContext(conversationId as string, messageId as string),
    enabled: conversationId !== undefined && messageId !== undefined,
    retry: false,
  });
}

export function useConvertMessage(
  conversationId: string,
  messageId: string,
): UseMutationResult<{ taskId: string }, unknown, ConversionDraft> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (draft: ConversionDraft) => convertMessage(conversationId, messageId, draft),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: messagesKey(conversationId) });
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

export function useTaskOrigin(taskId: string | undefined): UseQueryResult<TaskOrigin> {
  return useQuery({
    queryKey: ['chat', 'origin', taskId ?? 'none'],
    queryFn: () => fetchTaskOrigin(taskId as string),
    enabled: taskId !== undefined,
    retry: false,

    // Settled at creation and immutable after, so asking once is asking enough.
    staleTime: Infinity,
  });
}

export function useStartConversation(): UseMutationResult<ConversationRow, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (personId: string) => startConversation(personId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useCreateGroup(): UseMutationResult<ConversationRow, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => createGroup(name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useRoomMembership(): UseMutationResult<
  void,
  unknown,
  { conversationId: string; join: boolean }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, join }: { conversationId: string; join: boolean }) =>
      join ? joinRoom(conversationId) : leaveRoom(conversationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useRenameRoom(): UseMutationResult<
  void,
  unknown,
  { conversationId: string; name: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, name }: { conversationId: string; name: string }) =>
      renameRoom(conversationId, name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useMarkRead(): UseMutationResult<
  void,
  unknown,
  { conversationId: string; throughMessageId: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      conversationId,
      throughMessageId,
    }: {
      conversationId: string;
      throughMessageId: string;
    }) => markRead(conversationId, throughMessageId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useAssignmentContext(conversationId: string): UseQueryResult<AssignmentContext> {
  return useQuery({
    queryKey: ['chat', 'conversation', conversationId, 'assignment-context'],
    queryFn: () => fetchAssignmentContext(conversationId),
  });
}

export function useAssignTask(
  conversationId: string,
): UseMutationResult<{ taskId: string }, unknown, DirectTaskDraft> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (draft: DirectTaskDraft) => assignTaskInConversation(conversationId, draft),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: messagesKey(conversationId) });
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useStartRunInConversation(
  conversationId: string,
): UseMutationResult<{ instanceId: string }, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) => startRunInConversation(conversationId, templateId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: messagesKey(conversationId) });
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useProcessDraftFromMessages(
  conversationId: string,
): UseMutationResult<MessageSelectionDraft, unknown, readonly string[]> {
  return useMutation({
    mutationFn: (messageIds: readonly string[]) =>
      draftProcessFromMessages(conversationId, messageIds),
  });
}

export function useBuildProcessFromMessages(
  conversationId: string,
): UseMutationResult<
  { instanceId?: string },
  unknown,
  | { name: string; steps: readonly SubmittedProcessStep[] }
  | { templateId: string; steps: readonly SubmittedProcessStep[] }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (submission) => {
      if ('templateId' in submission) {
        await appendMessagesToTemplate(conversationId, submission);
        return {};
      }
      return startProcessFromMessages(conversationId, submission);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: messagesKey(conversationId) });
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}
