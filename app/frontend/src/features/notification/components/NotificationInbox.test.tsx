// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { NotificationPreferences, NotificationRow } from '../model/notification';
import { NotificationInbox } from './NotificationInbox';

const fetchNotifications = vi.fn<() => Promise<{ items: NotificationRow[] }>>();
const markNotificationRead = vi.fn<(id: string) => Promise<void>>();
const fetchNotificationPreferences = vi.fn<() => Promise<NotificationPreferences>>();
const saveNotificationPreferences =
  vi.fn<(preferences: NotificationPreferences) => Promise<void>>();

vi.mock('../api/notificationApi', () => ({
  fetchNotifications: () => fetchNotifications(),
  fetchUnreadCount: () => Promise.resolve({ unread: 0 }),
  markNotificationRead: (id: string) => markNotificationRead(id),
  fetchNotificationPreferences: () => fetchNotificationPreferences(),
  saveNotificationPreferences: (preferences: NotificationPreferences) =>
    saveNotificationPreferences(preferences),
}));

function lookup(key: string, vars?: Record<string, unknown>): string {
  const found = key
    .split('.')
    .reduce<unknown>(
      (node, step) =>
        typeof node === 'object' && node !== null
          ? (node as Record<string, unknown>)[step]
          : undefined,
      en,
    );
  const text = typeof found === 'string' ? found : key;
  return Object.entries(vars ?? {}).reduce(
    (sentence, [name, value]) => sentence.replaceAll(`{{${name}}}`, String(value)),
    text,
  );
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) => lookup(key, vars),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function row(over: Partial<NotificationRow> = {}): NotificationRow {
  return {
    id: 'n-1',
    kind: 'WORK_ASSIGNED',
    group: 'ASSIGNMENT',
    subjectKind: 'TASK',
    subjectId: 't-1',
    createdAt: '2026-08-24T09:00:00Z',
    readAt: null,
    items: [],
    ...over,
  };
}

const OPEN_SUBJECT = vi.fn();

function renderInbox() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <NotificationInbox onOpenSubject={OPEN_SUBJECT} />
    </QueryClientProvider>,
  );
}

function list(): HTMLElement {
  return document.querySelector('.fo-notif-list') as HTMLElement;
}

async function liveSwitches(): Promise<HTMLButtonElement[]> {
  await waitFor(() => {
    const controls = screen.getAllByRole('switch') as HTMLButtonElement[];
    expect(controls).toHaveLength(4);
    expect(controls.every((control) => !control.disabled)).toBe(true);
  });
  return screen.getAllByRole('switch') as HTMLButtonElement[];
}

beforeEach(() => {
  fetchNotifications.mockResolvedValue({ items: [] });
  markNotificationRead.mockResolvedValue(undefined);
  fetchNotificationPreferences.mockResolvedValue({
    assignment: true,
    time: true,
    process: false,
    weekly: true,
  });
  saveNotificationPreferences.mockResolvedValue(undefined);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('the inbox', () => {
  it('says "Nothing new." and nothing else', async () => {
    renderInbox();

    await waitFor(() => expect(list().textContent).toBe('Nothing new.'));
    expect(list().querySelectorAll('svg, img')).toHaveLength(0);
    expect(list().querySelectorAll('button')).toHaveLength(0);
    expect(list().children).toHaveLength(1);
  });

  it('marks a row read and opens its subject on one press', async () => {
    fetchNotifications.mockResolvedValue({ items: [row()] });

    renderInbox();

    await waitFor(() => expect(screen.getByText('Work was assigned to you')).toBeTruthy());
    fireEvent.click(screen.getByText('Work was assigned to you'));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith('n-1'));
    expect(OPEN_SUBJECT).toHaveBeenCalledWith({ kind: 'TASK', id: 't-1' });
  });

  it('expands a digest where it stands, opening nothing', async () => {
    fetchNotifications.mockResolvedValue({
      items: [
        row({
          id: 'd-1',
          kind: 'WEEKLY_SUMMARY',
          group: 'WEEKLY',
          subjectKind: 'WORKSPACE',

          subjectId: 'w-1',
          items: [
            row({ id: 'c-1', kind: 'RUN_COMPLETED', subjectKind: 'RUN', subjectId: 'r-1' }),
            row({ id: 'c-2', kind: 'STEP_STALLED', subjectKind: 'STEP', subjectId: 's-1' }),
          ],
        }),
      ],
    });

    renderInbox();

    const summary = await screen.findByText('2 things happened while you were away');
    expect(screen.queryByText('A run finished')).toBeNull();

    fireEvent.click(summary);

    expect(screen.getByText('A run finished')).toBeTruthy();
    expect(screen.getByText('A step has not moved')).toBeTruthy();

    expect(screen.getByText('2 things happened while you were away')).toBeTruthy();
    expect(OPEN_SUBJECT).not.toHaveBeenCalled();
  });

  it('says a vanished subject is no longer available, and offers no way in', async () => {
    fetchNotifications.mockResolvedValue({
      items: [row({ id: 'n-9', kind: 'WORK_APPROVED', subjectId: null })],
    });

    renderInbox();

    await waitFor(() => expect(screen.getByText('This is no longer available.')).toBeTruthy());
    expect(list().querySelectorAll('button')).toHaveLength(0);

    fireEvent.click(screen.getByText('Work was approved'));
    expect(OPEN_SUBJECT).not.toHaveBeenCalled();
  });

  it('offers no way to dismiss everything at once', async () => {
    fetchNotifications.mockResolvedValue({
      items: [row(), row({ id: 'n-2', kind: 'STALE_REVIEW' })],
    });

    renderInbox();
    await waitFor(() => expect(screen.getByText('A review is waiting')).toBeTruthy());

    for (const control of screen.getAllByRole('button')) {
      expect(control.textContent ?? '').not.toMatch(/mark all|read all|clear all|dismiss all/i);
    }
    expect(JSON.stringify(en)).not.toMatch(/markAll|readAll|clearAll/i);
  });

  it('paints no urgency on any row', async () => {
    fetchNotifications.mockResolvedValue({
      items: [
        row({ id: 'n-1', kind: 'OVERDUE_RUNG_3', group: 'ESCALATION' }),
        row({ id: 'n-2', kind: 'WORK_APPROVED', readAt: '2026-08-24T10:00:00Z' }),
      ],
    });

    renderInbox();
    await waitFor(() => expect(screen.getByText('Work is well past its date')).toBeTruthy());

    for (const node of list().querySelectorAll('li > *')) {
      const element = node as HTMLElement;
      expect(element.querySelectorAll('svg, img'), 'a row carries a glyph').toHaveLength(0);
      expect(element.getAttribute('style') ?? '').not.toMatch(
        /--alert|--at-risk|--waiting|--danger|--done|red|amber|orange/i,
      );
    }
  });

  it('gives nobody anything to sift the list with', async () => {
    fetchNotifications.mockResolvedValue({
      items: [row(), row({ id: 'n-2', kind: 'RUN_COMPLETED', group: 'PROCESS' })],
    });

    renderInbox();
    await waitFor(() => expect(screen.getByText('A run finished')).toBeTruthy());

    expect(list().querySelectorAll('input, select, textarea')).toHaveLength(0);

    expect(list().querySelectorAll('h1, h2, h3, h4, h5, h6, [role="group"]')).toHaveLength(0);
    expect(list().querySelectorAll('ul')).toHaveLength(1);
  });
});

describe('what interrupts you', () => {
  it('shows four switches and tells the reader the rule instead of a fifth', async () => {
    renderInbox();

    const controls = await liveSwitches();
    expect(controls.map((control) => control.getAttribute('aria-checked'))).toEqual([
      'true',
      'true',
      'false',
      'true',
    ]);

    for (const control of controls) {
      expect(control.textContent ?? '').not.toMatch(/escalat/i);
      expect(control.disabled).toBe(false);
    }

    expect(screen.getByText('Escalations always arrive.')).toBeTruthy();
    expect(screen.getByText('What interrupts you')).toBeTruthy();
  });

  it('sends the four together when one is changed', async () => {
    renderInbox();

    const controls = await liveSwitches();
    fireEvent.click(controls[1] as HTMLButtonElement);

    await waitFor(() =>
      expect(saveNotificationPreferences).toHaveBeenCalledWith({
        assignment: true,
        time: false,
        process: false,
        weekly: true,
      }),
    );
  });
});
