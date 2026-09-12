import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  fetchNotificationPreferences,
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
  saveNotificationPreferences,
} from '../api/notificationApi';
import type { NotificationPreferences, NotificationRow } from '../model/notification';

export const notificationKeys = {
  all: ['notification'] as const,

  inbox: () => ['notification', 'inbox'] as const,

  unread: () => ['notification', 'unread'] as const,

  preferences: () => ['notification', 'preferences'] as const,
} as const;

export function useUnreadCount(): UseQueryResult<{ unread: number }> {
  return useQuery({
    queryKey: notificationKeys.unread(),
    queryFn: fetchUnreadCount,

    retry: false,
  });
}

export function useInbox(enabled: boolean): UseQueryResult<{ items: NotificationRow[] }> {
  return useQuery({
    queryKey: notificationKeys.inbox(),
    queryFn: fetchNotifications,
    enabled,
    retry: false,
  });
}

export function useMarkRead(): UseMutationResult<void, unknown, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

export function usePreferences(enabled: boolean): UseQueryResult<NotificationPreferences> {
  return useQuery({
    queryKey: notificationKeys.preferences(),
    queryFn: fetchNotificationPreferences,
    enabled,
    retry: false,
  });
}

export function useSavePreferences(): UseMutationResult<void, unknown, NotificationPreferences> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (preferences: NotificationPreferences) => saveNotificationPreferences(preferences),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.preferences() });
    },
  });
}
