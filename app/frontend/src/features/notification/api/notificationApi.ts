import { apiRequest } from '../../../shared/api/client';
import type { NotificationPreferences, NotificationRow } from '../model/notification';

export function fetchNotifications(): Promise<{ items: NotificationRow[] }> {
  return apiRequest<{ items: NotificationRow[] }>('/notifications');
}

export function fetchUnreadCount(): Promise<{ unread: number }> {
  return apiRequest<{ unread: number }>('/notifications/unread-count');
}

export function markNotificationRead(id: string): Promise<void> {
  return apiRequest<void>(`/notifications/${id}/read`, { method: 'POST' });
}

export function fetchNotificationPreferences(): Promise<NotificationPreferences> {
  return apiRequest<NotificationPreferences>('/notification-preferences');
}

export function saveNotificationPreferences(preferences: NotificationPreferences): Promise<void> {
  return apiRequest<void>('/notification-preferences', {
    method: 'PUT',
    body: JSON.stringify(preferences),
  });
}
