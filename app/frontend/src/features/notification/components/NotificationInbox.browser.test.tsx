import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { JSX } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '../../../index.css';

import en from '../../../i18n/locales/en/common.json';
import type { NotificationPreferences, NotificationRow } from '../model/notification';
import { NotificationBell } from './NotificationBell';

let lengthened = false;

function stretch(value: string): string {
  return value
    .split(' ')
    .map((word) => (word.length > 3 ? word + word.slice(0, Math.ceil(word.length * 0.3)) : word))
    .join(' ');
}

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
  const filled = Object.entries(vars ?? {}).reduce(
    (sentence, [name, value]) => sentence.replaceAll(`{{${name}}}`, String(value)),
    text,
  );
  return lengthened ? stretch(filled) : filled;
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) => lookup(key, vars),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
  I18nextProvider: ({ children }: { children: unknown }) => children,
  Trans: ({ children }: { children: unknown }) => children,
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

const FULL: NotificationRow[] = [
  row({ id: 'n-1', kind: 'OVERDUE_RUNG_3' }),
  row({ id: 'n-2', kind: 'TASK_ATTACHED_TO_RUN', subjectKind: 'RUN', subjectId: 'r-1' }),

  row({ id: 'n-3', kind: 'DEADLINE_PROPOSED', subjectId: null }),
  row({ id: 'n-4', kind: 'WORK_APPROVED', readAt: '2026-08-24T10:00:00Z' }),
  row({
    id: 'd-1',
    kind: 'WEEKLY_SUMMARY',
    group: 'WEEKLY',
    subjectKind: 'WORKSPACE',
    subjectId: null,
    items: [
      row({ id: 'c-1', kind: 'STEP_STALLED', subjectKind: 'STEP', subjectId: 's-1' }),
      row({ id: 'c-2', kind: 'RUN_COMPLETED', subjectKind: 'RUN', subjectId: 'r-2' }),
      row({ id: 'c-3', kind: 'LONG_BLOCK_2' }),
    ],
  }),
];

vi.mock('../api/notificationApi', () => ({
  fetchNotifications: (): Promise<{ items: NotificationRow[] }> => Promise.resolve({ items: FULL }),
  fetchUnreadCount: (): Promise<{ unread: number }> => Promise.resolve({ unread: 12 }),
  markNotificationRead: (): Promise<void> => Promise.resolve(undefined),
  fetchNotificationPreferences: (): Promise<NotificationPreferences> =>
    Promise.resolve({ assignment: true, time: false, process: true, weekly: true }),
  saveNotificationPreferences: (): Promise<void> => Promise.resolve(undefined),
}));

function InTheTopBar(): JSX.Element {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return (
    <QueryClientProvider client={client}>
      <div className="fo-topbar">
        <nav className="fo-tabbar">
          <button type="button" className="fo-tab">
            {lookup('shell.nav.today')}
          </button>
          <button type="button" className="fo-tab">
            {lookup('shell.nav.tasks')}
          </button>
        </nav>
        <div
          style={{
            marginInlineStart: 'auto',
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--space-3)',
          }}
        >
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
            {lookup('shell.updatedJustNow')}
          </span>
          <NotificationBell onOpenSubject={() => undefined} />
          <button type="button" className="ui-button ui-button-primary">
            {lookup('shell.newProcess')}
          </button>
        </div>
      </div>
    </QueryClientProvider>
  );
}

async function openTheInbox(): Promise<HTMLElement> {
  render(<InTheTopBar />);
  fireEvent.click(
    screen.getByRole('button', { name: new RegExp(lookup('notification.bell.label')) }),
  );
  await waitFor(() => expect(document.querySelectorAll('.fo-notif-list li').length).toBe(5));
  return screen.getByRole('dialog');
}

function overlaps(a: DOMRect, b: DOMRect): boolean {
  return (
    a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1
  );
}

afterEach(cleanup);

beforeEach(() => {
  lengthened = false;
});

describe.each([
  ['English', false],
  ['English at +30%', true],
])('the inbox panel at 1280px, %s', (_name, longer) => {
  beforeEach(() => {
    lengthened = longer;
  });

  it('never forces the page body to scroll sideways', async () => {
    await openTheInbox();

    const page = document.documentElement;
    expect(
      Math.round(page.scrollWidth),
      `the page body scrolls horizontally at ${lengthened ? '+30%' : 'English'}`,
    ).toBeLessThanOrEqual(Math.round(page.clientWidth));
    expect(Math.round(document.body.scrollWidth)).toBeLessThanOrEqual(
      Math.round(document.body.clientWidth),
    );
  });

  it('keeps the whole panel inside the viewport, at both edges', async () => {
    const panel = await openTheInbox();
    const box = panel.getBoundingClientRect();

    expect(Math.round(box.right), 'the panel leaves the right edge').toBeLessThanOrEqual(
      document.documentElement.clientWidth,
    );
    expect(Math.round(box.left), 'the panel leaves the left edge').toBeGreaterThanOrEqual(0);
  });

  it('paints no row on top of another and keeps every part inside its row', async () => {
    const panel = await openTheInbox();

    const rows = Array.from(panel.querySelectorAll('.fo-notif-list li > *')) as HTMLElement[];
    expect(rows.length).toBe(5);

    const boxes = rows.map((element) => element.getBoundingClientRect());
    for (const [i, a] of boxes.entries()) {
      for (const [j, b] of boxes.entries()) {
        if (j <= i) {
          continue;
        }
        expect(
          overlaps(a, b),
          `rows ${i} and ${j} share pixels at ${lengthened ? '+30%' : 'English'}`,
        ).toBe(false);
      }
    }

    for (const element of rows) {
      const edge = element.getBoundingClientRect().right;
      expect(
        element.scrollWidth,
        `${element.textContent} overflows its own row`,
      ).toBeLessThanOrEqual(element.clientWidth + 1);

      for (const node of element.querySelectorAll('*')) {
        const child = node as HTMLElement;
        const box = child.getBoundingClientRect();
        if (box.width === 0) {
          continue;
        }
        expect(
          Math.round(box.right),
          `${child.className || child.nodeName} crosses its row's right edge`,
        ).toBeLessThanOrEqual(Math.round(edge) + 1);
      }
    }
  });

  it('shows every sentence in full rather than clipping it', async () => {
    const panel = await openTheInbox();

    for (const node of panel.querySelectorAll('.fo-notif-list li > *')) {
      const element = node as HTMLElement;
      expect(element.scrollHeight, `${element.textContent} is clipped`).toBeLessThanOrEqual(
        element.clientHeight + 1,
      );
    }
  });

  it('keeps every switch beside its own label, inside the panel', async () => {
    const panel = await openTheInbox();
    const edge = panel.getBoundingClientRect().right;

    const switches = Array.from(panel.querySelectorAll('[role="switch"]')) as HTMLElement[];
    expect(switches.length).toBe(4);

    for (const control of switches) {
      const box = control.getBoundingClientRect();
      expect(box.width, 'a switch was squeezed').toBeGreaterThanOrEqual(30);
      expect(Math.round(box.right), 'a switch leaves the panel').toBeLessThanOrEqual(
        Math.round(edge) + 1,
      );
    }
  });

  it('scrolls itself rather than the page, and never sideways', async () => {
    const panel = await openTheInbox();

    expect(Math.round(panel.scrollWidth)).toBeLessThanOrEqual(Math.round(panel.clientWidth) + 1);
    expect(panel.getBoundingClientRect().height).toBeLessThanOrEqual(
      document.documentElement.clientHeight,
    );
  });

  it('holds its shape once a digest is expanded', async () => {
    const panel = await openTheInbox();

    fireEvent.click(screen.getByText(lookup('notification.digest.summary', { count: 3 })));
    await waitFor(() => expect(panel.querySelectorAll('.fo-notif-list li').length).toBe(8));

    expect(Math.round(panel.getBoundingClientRect().right)).toBeLessThanOrEqual(
      document.documentElement.clientWidth,
    );
    expect(Math.round(panel.scrollWidth)).toBeLessThanOrEqual(Math.round(panel.clientWidth) + 1);
    expect(Math.round(document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      Math.round(document.documentElement.clientWidth),
    );
  });
});
