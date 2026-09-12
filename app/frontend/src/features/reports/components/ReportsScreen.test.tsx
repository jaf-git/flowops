// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ReportSummary } from '../model/summary';
import { ReportsScreen } from './ReportsScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function report(over: Partial<ReportSummary> = {}): ReportSummary {
  return {
    runs: 4,
    activeRuns: 3,
    closed: 6,
    total: 12,
    completionPercent: 50,
    blocked: 2,
    awaitingAssignment: 1,
    needsAttention: 3,
    openHours: 18.5,
    unestimatedOpen: 0,
    distribution: [
      { state: 'done', count: 6, share: 0.5 },
      { state: 'inProgress', count: 3, share: 0.25 },
      { state: 'blocked', count: 2, share: 1 / 6 },
      { state: 'notStarted', count: 1, share: 1 / 12 },
    ],
    waiting: [
      {
        runId: 'i1',
        runName: 'Comandă mobilier',
        stepTitle: 'Sună furnizorul',
        minutes: 480,
        share: 1,
      },
    ],
    ...over,
  };
}

function renderReport(summary: ReportSummary = report(), loading = false): void {
  render(<ReportsScreen report={summary} loading={loading} locale="en" />);
}

function tileValues(): (string | null)[] {
  return screen.getAllByText(/./, { selector: '.fo-stat-value' }).map((tile) => tile.textContent);
}

describe('the reports tab', () => {
  it('leads with the four figures the contract can prove, in one order', () => {
    renderReport();

    expect(tileValues()).toEqual(['3', '6 / 12', '3', '18.5h']);
  });

  it('says how much open work carries no estimate, so the total is never read as complete', () => {
    renderReport(report({ openHours: 4, unestimatedOpen: 3 }));

    expect(screen.getByText(/reports\.tile\.unestimated.*"count":3/)).toBeDefined();
  });

  it('says nothing about estimates when every open step carries one', () => {
    renderReport(report({ unestimatedOpen: 0 }));

    expect(screen.queryByText(/reports\.tile\.unestimated/)).toBeNull();
  });

  it('splits what needs attention into what is stuck and what belongs to nobody', () => {
    renderReport();

    expect(screen.getByText(/"blocked":2.*"unassigned":1/)).toBeDefined();
  });

  it('names the step that is waiting and the run it is in', () => {
    renderReport();

    expect(screen.getByText('Sună furnizorul')).toBeDefined();
    expect(screen.getByText('Comandă mobilier')).toBeDefined();
  });

  it('says plainly that nothing is held up rather than showing an empty panel', () => {
    renderReport(report({ waiting: [] }));

    expect(screen.getByText('reports.waiting.empty')).toBeDefined();
  });

  it('charts every state, including the ones at nothing', () => {
    renderReport(
      report({
        distribution: [
          { state: 'done', count: 2, share: 1 },
          { state: 'inProgress', count: 0, share: 0 },
          { state: 'blocked', count: 0, share: 0 },
          { state: 'notStarted', count: 0, share: 0 },
        ],
      }),
    );

    expect(screen.getAllByText(/canvas\.board\.state\./)).toHaveLength(4);
  });

  it('says it is reading rather than reporting nothing while it waits', () => {
    renderReport(report({ runs: 0, activeRuns: 0, total: 0 }), true);

    expect(screen.getByText('reports.loading')).toBeDefined();
    expect(screen.queryByText('reports.waiting.empty')).toBeNull();
  });

  it('puts no person on the screen at all', () => {
    renderReport();

    expect(document.body.textContent).not.toMatch(/member|assignee|owner/i);
  });
});
