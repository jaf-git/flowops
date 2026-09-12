// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { NotificationPreferences, NotificationRow } from '../model/notification';
import { NotificationBell } from './NotificationBell';

const fetchUnreadCount = vi.fn<() => Promise<{ unread: number }>>();

vi.mock('../api/notificationApi', () => ({
  fetchNotifications: (): Promise<{ items: NotificationRow[] }> => Promise.resolve({ items: [] }),
  fetchUnreadCount: () => fetchUnreadCount(),
  markNotificationRead: () => Promise.resolve(undefined),
  fetchNotificationPreferences: (): Promise<NotificationPreferences> =>
    Promise.resolve({ assignment: true, time: true, process: true, weekly: true }),
  saveNotificationPreferences: () => Promise.resolve(undefined),
}));

function lookup(key: string): string {
  const found = key
    .split('.')
    .reduce<unknown>(
      (node, step) =>
        typeof node === 'object' && node !== null
          ? (node as Record<string, unknown>)[step]
          : undefined,
      en,
    );
  return typeof found === 'string' ? found : key;
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => lookup(key), i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function renderBell() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <NotificationBell onOpenSubject={vi.fn()} />
    </QueryClientProvider>,
  );
}

function bell(): HTMLButtonElement {
  return screen.getByRole('button', { name: /Notifications/ }) as HTMLButtonElement;
}

beforeEach(() => {
  fetchUnreadCount.mockResolvedValue({ unread: 0 });
  document.title = 'FlowOps';
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('the bell', () => {
  it('shows the count as plain text, with nothing drawn around it', async () => {
    fetchUnreadCount.mockResolvedValue({ unread: 4 });

    renderBell();

    await waitFor(() => expect(screen.getByText('4')).toBeTruthy());
    const count = screen.getByText('4');

    const style = count.getAttribute('style') ?? '';
    expect(style, 'the count is filled').not.toMatch(/background/i);
    expect(style, 'the count is a pill').not.toMatch(/border-radius|border:/i);
    expect(style, 'the count moves').not.toMatch(/animation|transform|transition/i);
    expect(style, 'the count is coloured for alarm').not.toMatch(/--alert|--danger|red|crimson/i);

    expect(bell().querySelectorAll('svg')).toHaveLength(1);
    expect(bell().querySelectorAll('svg path')).toHaveLength(1);
  });

  it('prints no number at all when nothing is unread, and stays where it is', async () => {
    renderBell();

    await waitFor(() => expect(fetchUnreadCount).toHaveBeenCalled());
    expect(bell()).toBeTruthy();
    expect(screen.queryByText('0')).toBeNull();
    expect(bell().textContent).toBe('Notifications');
  });

  it('leaves the tab title alone', async () => {
    fetchUnreadCount.mockResolvedValue({ unread: 7 });

    renderBell();

    await waitFor(() => expect(screen.getByText('7')).toBeTruthy());
    expect(document.title).toBe('FlowOps');

    fireEvent.click(bell());
    await waitFor(() => expect(screen.getByText('Nothing new.')).toBeTruthy());
    expect(document.title).toBe('FlowOps');
  });

  it('opens the panel and closes it on Escape', async () => {
    renderBell();

    fireEvent.click(bell());
    await waitFor(() => expect(screen.getByRole('dialog')).toBeTruthy());

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(document.activeElement).toBe(bell());
  });
});
