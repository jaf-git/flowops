// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { ConversationBracket, ConversationWait } from '../api/bracketApi';
import { AnalyticsTab } from './AnalyticsTab';

const fetchConversationWork = vi.fn<(id: string) => Promise<ConversationBracket[]>>();
const fetchConversationWaits = vi.fn<(id: string) => Promise<ConversationWait[]>>();

vi.mock('../api/bracketApi', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  fetchConversationWork: (id: string) => fetchConversationWork(id),
  fetchConversationWaits: (id: string) => fetchConversationWaits(id),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

function bracket(over: Partial<ConversationBracket> = {}): ConversationBracket {
  return {
    bracketId: `b-${Math.random().toString(36).slice(2)}`,
    address: 'Aurora › design',
    workType: 'DESIGN',
    state: 'OPEN',
    closeKind: null,
    outputValue: null,
    outputKind: null,
    performerId: 'karim',
    performerName: 'Karim',
    messageIds: ['m-1'],
    openedAt: '2026-08-01T09:00:00Z',
    lastActivityAt: '2026-08-02T09:00:00Z',
    nudged: false,
    openWaits: 0,
    live: true,
    ...over,
  };
}

function wait(over: Partial<ConversationWait> = {}): ConversationWait {
  return {
    waitId: `w-${Math.random().toString(36).slice(2)}`,
    bracketId: 'b-1',
    kind: 'COLLEAGUE',
    external: false,
    reason: 'the captions',
    expectedBy: null,
    openedAt: '2026-08-02T09:00:00Z',
    blockingAddress: null,
    ...over,
  };
}

function renderTab() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <AnalyticsTab conversationId="c-1" />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchConversationWork.mockReset().mockResolvedValue([]);
  fetchConversationWaits.mockReset().mockResolvedValue([]);
});

afterEach(cleanup);

describe('where this room’s work goes', () => {
  it('counts work and never a person, even when every bracket names one', async () => {
    fetchConversationWork.mockResolvedValue([
      bracket({ workType: 'DESIGN', performerName: 'Karim', closeKind: 'DELIVERED', live: false }),
      bracket({ workType: 'DESIGN', performerName: 'Karim', closeKind: 'DROPPED', live: false }),
      bracket({ workType: 'VIDEO', performerName: 'Nour' }),
    ]);

    renderTab();

    expect(await screen.findByText('DESIGN')).toBeTruthy();
    expect(screen.getByText('VIDEO')).toBeTruthy();

    expect(screen.queryByText(/Karim/)).toBeNull();
    expect(screen.queryByText(/Nour/)).toBeNull();
  });

  it('separates work that arrived from work that merely ended', async () => {
    fetchConversationWork.mockResolvedValue([
      bracket({ closeKind: 'DELIVERED', live: false }),
      bracket({ closeKind: 'DONE', live: false }),
      bracket({ closeKind: 'HANDED_OVER', live: false }),
      bracket({ closeKind: 'DROPPED', live: false }),
      bracket({ live: true }),
    ]);

    renderTab();

    expect(await screen.findByText('2')).toBeTruthy();

    expect(screen.getByText('discovery.analytics.endings.eyebrow')).toBeTruthy();
  });

  it('says how much of the waiting is outside this workspace', async () => {
    fetchConversationWork.mockResolvedValue([bracket()]);
    fetchConversationWaits.mockResolvedValue([
      wait({ kind: 'CLIENT', external: true }),
      wait({ kind: 'COLLEAGUE', external: false }),
    ]);

    renderTab();

    expect(await screen.findByText('discovery.analytics.waiting.outside')).toBeTruthy();
    expect(screen.queryByText('discovery.analytics.waiting.allInside')).toBeNull();
  });

  it('says so when all the waiting is on people here', async () => {
    fetchConversationWork.mockResolvedValue([bracket()]);
    fetchConversationWaits.mockResolvedValue([wait({ kind: 'COLLEAGUE', external: false })]);

    renderTab();

    expect(await screen.findByText('discovery.analytics.waiting.allInside')).toBeTruthy();
  });

  it('does not say where the waiting is when nothing is waiting', async () => {
    fetchConversationWork.mockResolvedValue([bracket()]);
    fetchConversationWaits.mockResolvedValue([]);

    renderTab();

    expect(await screen.findByText('discovery.analytics.waiting.none')).toBeTruthy();
    expect(screen.queryByText('discovery.analytics.waiting.allInside')).toBeNull();
    expect(screen.queryByText('discovery.analytics.waiting.outside')).toBeNull();
  });

  it('names a vocabulary that has one word per piece of work', async () => {
    fetchConversationWork.mockResolvedValue([
      bracket({ workType: 'RESEARCH' }),
      bracket({ workType: 'COMPETITOR_ANALYSIS' }),
    ]);

    renderTab();

    expect(await screen.findByText('discovery.analytics.kinds.everyOneDifferent')).toBeTruthy();
  });

  it('invites rather than reporting nothing five times over', async () => {
    renderTab();

    expect(await screen.findByText('discovery.analytics.empty.what')).toBeTruthy();
    expect(screen.queryByText('0')).toBeNull();
  });
});
