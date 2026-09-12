// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
const evidenceFor = vi.fn();
const applied = vi.fn();
const dismissed = vi.fn();
vi.mock('../api/insightApi', () => ({
  insightsFor: (_subjectType: string, id: string) => insightsForTemplate(id),
  applyInsight: (insight: unknown) => applied(insight),
  dismissInsight: (insight: unknown) => dismissed(insight),
  evidenceFor: (id: string) => evidenceFor(id),
}));

const offerAsDownload = vi.fn();
vi.mock('../model/download', () => ({
  offerAsDownload: (file: Blob, name: string) => offerAsDownload(file, name),
}));

afterEach(() => {
  cleanup();
  insightsForTemplate.mockReset();
  evidenceFor.mockReset();
  offerAsDownload.mockReset();
});

const finding: Insight = {
  kind: 'MISSING_STEP',
  findingKey: 'send-reminder-at-2',
  action: 'INSERT_STEP',
  subjectId: 't-1',
  subjectName: 'Client onboarding',
  stepTitle: 'Send reminder',
  afterStep: 'Kickoff',
  beforeStep: null,
  occurrences: 5,
  runsTotal: 7,
  windowFrom: '2026-04-06T08:00:00Z',
  windowTo: '2026-06-11T03:26:00Z',
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
  instances: ['a', 'b', 'c', 'd', 'e'],
};

function show(): void {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <TemplateInsights templateId="t-1" />
    </QueryClientProvider>,
  );
}

describe('Explain', () => {
  it('hands over the whole template history, so the denominator is checkable too', async () => {
    insightsForTemplate.mockResolvedValue([finding]);
    evidenceFor.mockResolvedValue(new Blob(['archive']));

    show();

    await waitFor(() => expect(screen.getByText('insight.explain')).toBeTruthy());
    await userEvent.click(screen.getByText('insight.explain'));

    await waitFor(() => {
      expect(evidenceFor).toHaveBeenCalledWith('t-1');
      expect(offerAsDownload).toHaveBeenCalledTimes(1);
    });
  });

  it('says so when the evidence cannot be fetched, rather than failing silently', async () => {
    insightsForTemplate.mockResolvedValue([finding]);
    evidenceFor.mockRejectedValue(new Error('unreachable'));

    show();

    await waitFor(() => expect(screen.getByText('insight.explain')).toBeTruthy());
    await userEvent.click(screen.getByText('insight.explain'));

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toContain('insight.explainFailed');
    });
    expect(offerAsDownload).not.toHaveBeenCalled();
  });
});
