// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { EstimateDivergenceCard } from './EstimateDivergenceCard';
import { aDecision } from './insightFixtures';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) =>
      vars ? `${key} ${JSON.stringify(vars)}` : key,
  }),
}));

afterEach(cleanup);

function estimateDivergence(over: Partial<Insight> = {}): Insight {
  return {
    kind: 'ESTIMATE_DIVERGENCE',
    findingKey: 'estimate',
    action: 'UPDATE_ESTIMATE',
    subjectId: 't-1',
    subjectName: 'Prepare quote',
    stepTitle: null as unknown as string,
    afterStep: null,
    beforeStep: null,
    occurrences: 40,
    runsTotal: 66,
    medianPhases: null,
    reason: null,
    dependsOnStep: null,
    daysSinceLastUse: null,
    windowDays: null,
    expectedIntervalDays: null,
    estimatedMs: 2 * 3_600_000,
    medianWorkMs: 5 * 3_600_000,
    fastestMiddleMs: 4 * 3_600_000,
    slowestMiddleMs: 6 * 3_600_000,
    spreadIsWide: false,
    excludedRuns: null,
    qualifyingPopulation: null,
    windowFrom: '2026-05-24T08:00:00Z',
    windowTo: '2026-08-24T08:00:00Z',
    instances: [],
    ...over,
  };
}

describe('the evidence line', () => {
  it('states what the figure could not see, where the exclusion was material', () => {
    render(
      <EstimateDivergenceCard
        insight={estimateDivergence({ excludedRuns: 26, qualifyingPopulation: 66 })}
        decision={aDecision()}
      />,
    );

    expect(screen.getByTestId('closure-coverage').textContent).toContain('26');
  });

  it('renders no clause at all where nothing material was excluded', () => {
    render(<EstimateDivergenceCard insight={estimateDivergence()} decision={aDecision()} />);

    expect(
      screen.queryByTestId('closure-coverage'),
      'absent, not zero: "0 further tasks never closed" on every card would train people to stop reading the line that matters',
    ).toBeNull();
  });

  it('does not turn a zero into a clause, if a server ever sent one', () => {
    render(
      <EstimateDivergenceCard
        insight={estimateDivergence({ excludedRuns: 0, qualifyingPopulation: 40 })}
        decision={aDecision()}
      />,
    );

    expect(screen.queryByTestId('closure-coverage')).not.toBeNull();
  });

  it('names the spread only where the middle of the sample is genuinely wide', () => {
    const { unmount } = render(
      <EstimateDivergenceCard insight={estimateDivergence()} decision={aDecision()} />,
    );
    expect(screen.queryByText(/estimateDivergence\.spread/)).toBeNull();
    unmount();

    render(
      <EstimateDivergenceCard
        insight={estimateDivergence({ spreadIsWide: true })}
        decision={aDecision()}
      />,
    );
    expect(screen.queryByText(/estimateDivergence\.spread/)).not.toBeNull();
  });

  it('offers the median as a suggestion where the template has no estimate at all', () => {
    render(
      <EstimateDivergenceCard
        insight={estimateDivergence({ estimatedMs: null })}
        decision={aDecision()}
      />,
    );

    expect(screen.queryByText(/estimateDivergence\.claimNoEstimate/)).not.toBeNull();
  });
});
