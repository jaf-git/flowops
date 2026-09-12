// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  fetchNotificationPreferences,
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
  saveNotificationPreferences,
} from './notificationApi';

const apiRequest = vi.fn<(path: string, init?: RequestInit) => Promise<unknown>>();

vi.mock('../../../shared/api/client', () => ({
  apiRequest: (path: string, init?: RequestInit) => apiRequest(path, init),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe("NOTIFICATION's five doors", () => {
  it('names nobody, in any path or any body', async () => {
    apiRequest.mockResolvedValue(undefined);

    await fetchNotifications();
    await fetchUnreadCount();
    await markNotificationRead('n-1');
    await fetchNotificationPreferences();
    await saveNotificationPreferences({
      assignment: true,
      time: false,
      process: true,
      weekly: false,
    });

    expect(apiRequest).toHaveBeenCalledTimes(5);

    const naming = /person|people|user|assignee|member|employee|for=|subject=/i;
    for (const [path, init] of apiRequest.mock.calls) {
      expect(path, `${path} names somebody`).not.toMatch(naming);

      expect(path, `${path} carries a query string`).not.toContain('?');
      expect(String(init?.body ?? ''), 'a body names somebody').not.toMatch(naming);
    }
  });

  it('calls the five paths the backend was built against', async () => {
    apiRequest.mockResolvedValue(undefined);

    await fetchNotifications();
    await fetchUnreadCount();
    await markNotificationRead('n-1');
    await fetchNotificationPreferences();
    await saveNotificationPreferences({
      assignment: true,
      time: true,
      process: true,
      weekly: true,
    });

    expect(apiRequest.mock.calls.map(([path, init]) => `${init?.method ?? 'GET'} ${path}`)).toEqual(
      [
        'GET /notifications',
        'GET /notifications/unread-count',
        'POST /notifications/n-1/read',
        'GET /notification-preferences',
        'PUT /notification-preferences',
      ],
    );
  });

  it('exposes no bulk way to dismiss anything', async () => {
    const surface = await import('./notificationApi');

    expect(Object.keys(surface).filter((name) => /all|bulk|clear|dismiss/i.test(name))).toEqual([]);
  });
});
