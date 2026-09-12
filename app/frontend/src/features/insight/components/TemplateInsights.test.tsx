// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { TemplateInsights } from './TemplateInsights';

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => undefined },
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const insightsForTemplate = vi.fn();

const evidence = vi.fn();
const applied = vi.fn();
const dismissed = vi.fn();
vi.mock('../api/insightApi', () => ({
  insightsFor: (_subject: string, id: string) => insightsForTemplate(id),
  applyInsight: (insight: unknown) => applied(insight),
  dismissInsight: (insight: unknown) => dismissed(insight),
  evidenceFor: (id: string) => evidence(id),
}));

afterEach(() => {
  cleanup();
  insightsForTemplate.mockReset();
});

function show(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <TemplateInsights templateId="t-1" />
    </QueryClientProvider>,
  );
}

const finding: Insight = {
  kind: 'MISSING_STEP',
  findingKey: 'send-reminder-at-2',
  action: 'INSERT_STEP',
  subjectId: 't-1',
  subjectName: 'Client onboarding',
  stepTitle: 'Send reminder',
  afterStep: 'Kickoff',
  beforeStep: null,
  occurrences: 8,
  runsTotal: 12,
  windowFrom: '2026-04-06T08:00:00Z',
  windowTo: '2026-08-05T07:00:00Z',
  medianPhases: null,
  reason: null,
  dependsOnStep: null,
  daysSinceLastUse: null,
  windowDays: null,

  expectedIntervalDays: null,
  estimatedMs: null,
  medianWorkMs: null,
  fastestMiddleMs: null,
  slowestMiddleMs: null,
  spreadIsWide: null,
  excludedRuns: null,
  qualifyingPopulation: null,
  instances: ['a', 'b', 'c'],
};

describe('TemplateInsights', () => {
  it('shows a finding when the history has one', async () => {
    insightsForTemplate.mockResolvedValue([finding]);

    show();

    await waitFor(() => {
      expect(screen.getByText(/insight\.missingStep\.claim/)).toBeTruthy();
    });
  });

  it('renders nothing at all when there is nothing to say', async () => {
    insightsForTemplate.mockResolvedValue([]);

    const { container } = render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <TemplateInsights templateId="t-1" />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(insightsForTemplate).toHaveBeenCalled();
    });
    expect(container.textContent).toBe('');
  });

  it('stays silent when the request fails', async () => {
    insightsForTemplate.mockRejectedValue(new Error('unreachable'));

    const { container } = render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <TemplateInsights templateId="t-1" />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(insightsForTemplate).toHaveBeenCalled();
    });
    expect(container.textContent).toBe('');
  });

  it('asks about the template it was given', async () => {
    insightsForTemplate.mockResolvedValue([]);

    show();

    await waitFor(() => {
      expect(insightsForTemplate).toHaveBeenCalledWith('t-1');
    });
  });

  it('renders one card per finding', async () => {
    insightsForTemplate.mockResolvedValue([
      finding,
      { ...finding, findingKey: 'book-the-room-at-3', stepTitle: 'Book the room', occurrences: 4 },
    ]);

    show();

    await waitFor(() => {
      expect(screen.getAllByText(/insight\.missingStep\.claim/)).toHaveLength(2);
    });
  });
});
