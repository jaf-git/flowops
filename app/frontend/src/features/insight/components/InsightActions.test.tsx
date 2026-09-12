// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { InsightActions } from './InsightActions';
import { aDecision } from './insightFixtures';

const KNOWN = new Set(['insight.refusal.NOT_THE_AUTHOR']);

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => {
      if (options !== undefined && 'defaultValue' in options) {
        return KNOWN.has(key) ? key : (options.defaultValue as string);
      }
      return options === undefined ? key : `${key} ${JSON.stringify(options)}`;
    },
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function finding(over: Partial<Insight> = {}): Insight {
  return {
    kind: 'MISSING_STEP',
    findingKey: 'send-reminder-at-2',
    action: 'INSERT_STEP',
    subjectId: 't-1',
    subjectName: 'Client onboarding',
    stepTitle: 'Send reminder',
    afterStep: 'Proposal',
    beforeStep: 'Contract',
    occurrences: 8,
    runsTotal: 12,
    medianPhases: null,
    reason: null,
    dependsOnStep: null,
    daysSinceLastUse: null,
    windowDays: null,
    windowFrom: '2026-04-06T08:00:00Z',
    windowTo: '2026-06-11T03:26:00Z',

    expectedIntervalDays: null,
    estimatedMs: null,
    medianWorkMs: null,
    fastestMiddleMs: null,
    slowestMiddleMs: null,
    spreadIsWide: null,
    excludedRuns: null,
    qualifyingPopulation: null,
    instances: ['a', 'b'],
    ...over,
  };
}

describe('InsightActions', () => {
  it('offers Apply where the finding proposes a change', async () => {
    const apply = vi.fn();
    render(<InsightActions insight={finding()} decision={aDecision({ apply })} />);

    await userEvent.click(screen.getByText('insight.apply'));

    expect(apply).toHaveBeenCalledOnce();
  });

  it('does not render Apply at all where the finding proposes nothing', () => {
    render(<InsightActions insight={finding({ action: null })} decision={aDecision()} />);

    expect(screen.queryByText('insight.apply')).toBeNull();
  });

  it('offers Dismiss even where there is nothing to apply', async () => {
    const dismiss = vi.fn();
    render(
      <InsightActions insight={finding({ action: null })} decision={aDecision({ dismiss })} />,
    );

    await userEvent.click(screen.getByText('insight.dismiss'));

    expect(dismiss).toHaveBeenCalledOnce();
  });

  it("falls back to the subject feature's own sentence for a code it has no translation for", () => {
    const refusal = {
      code: 'SOMETHING_ONLY_PROCESS_KNOWS',
      message: 'That step no longer exists.',
    };
    render(
      <InsightActions insight={finding()} decision={aDecision({ refusalFor: () => refusal })} />,
    );

    expect(screen.getByRole('alert').textContent).toBe(refusal.message);
  });

  it('renders the translation written for that exact code when there is one', () => {
    const refusal = { code: 'NOT_THE_AUTHOR', message: 'the server sentence' };
    render(
      <InsightActions insight={finding()} decision={aDecision({ refusalFor: () => refusal })} />,
    );

    expect(screen.getByRole('alert').textContent).toBe('insight.refusal.NOT_THE_AUTHOR');
  });

  it('shows a refusal only on the finding it was about', () => {
    const other = finding({ findingKey: 'chase-the-client-at-4' });
    render(
      <InsightActions
        insight={other}
        decision={aDecision({
          refusalFor: (insight) =>
            insight.findingKey === 'send-reminder-at-2'
              ? { code: 'NOT_PERMITTED', message: 'not yours to change' }
              : null,
        })}
      />,
    );

    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('goes quiet while its own decision is in flight, and leaves its neighbours alone', () => {
    const { rerender } = render(
      <InsightActions
        insight={finding()}
        decision={aDecision({ deciding: 'send-reminder-at-2' })}
      />,
    );

    expect((screen.getByText('insight.apply') as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByText('insight.dismiss') as HTMLButtonElement).disabled).toBe(true);

    rerender(
      <InsightActions insight={finding()} decision={aDecision({ deciding: 'somebody else' })} />,
    );

    expect((screen.getByText('insight.apply') as HTMLButtonElement).disabled).toBe(false);
  });

  it('has no Explain where the reader may not take an export', () => {
    render(<InsightActions insight={finding()} decision={aDecision()} />);

    expect(screen.queryByText('insight.explain')).toBeNull();
  });
});
