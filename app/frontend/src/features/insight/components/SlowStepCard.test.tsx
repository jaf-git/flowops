// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { aDecision } from './insightFixtures';
import { SlowStepCard } from './SlowStepCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

const HOUR = 3_600_000;

function slowStep(over: Partial<Insight> = {}): Insight {
  return {
    kind: 'SLOW_STEP',
    findingKey: 'k-1',
    action: null,
    subjectId: 't-1',
    subjectName: 'Client onboarding',
    stepTitle: 'Contract',
    afterStep: null,
    beforeStep: null,
    occurrences: 7,
    runsTotal: 7,
    medianPhases: { workMs: 4 * HOUR, blockedMs: 0, waitingMs: 40 * HOUR, reviewMs: 5 * HOUR },
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
    instances: [],
    ...over,
  };
}

describe('SlowStepCard', () => {
  it('names the step', () => {
    render(<SlowStepCard insight={slowStep()} decision={aDecision()} />);

    expect(screen.getByText(/insight\.slowStep\.claim/).textContent).toContain('"step":"Contract"');
  });

  it('shows what kind of slow it is, phase by phase', () => {
    const { container } = render(<SlowStepCard insight={slowStep()} decision={aDecision()} />);

    const text = container.textContent ?? '';
    expect(text).toMatch(/40|waiting|wait/i);
    expect(text).toMatch(/4|work|active/i);
  });

  it('cannot print a total, because it has no way to', () => {
    const { container } = render(<SlowStepCard insight={slowStep()} decision={aDecision()} />);

    expect(container.textContent).not.toContain('49');
  });

  it('omits a phase nothing was spent in', () => {
    const { container } = render(<SlowStepCard insight={slowStep()} decision={aDecision()} />);

    expect(container.textContent?.toLowerCase()).not.toContain('blocked');
  });

  it('renders without phases rather than throwing, if the server ever sends none', () => {
    const { container } = render(
      <SlowStepCard insight={slowStep({ medianPhases: null })} decision={aDecision()} />,
    );

    expect(container.textContent).toContain('insight.slowStep.claim');
  });

  it('names nobody', () => {
    const { container } = render(<SlowStepCard insight={slowStep()} decision={aDecision()} />);

    expect(container.textContent).not.toMatch(/assignee|Andrei|Maria|Ionu/);
  });

  it('signals no urgency with colour, in either direction', () => {
    const { container } = render(<SlowStepCard insight={slowStep()} decision={aDecision()} />);

    const card = container.querySelector('article');

    expect(card?.getAttribute('style')).not.toContain('var(--alert)');
    expect(card?.getAttribute('style')).not.toContain('var(--waiting)');
  });

  it('offers no Apply', () => {
    render(<SlowStepCard insight={slowStep()} decision={aDecision()} onExplain={vi.fn()} />);

    const labels = screen.getAllByRole('button').map((button) => button.textContent);
    expect(labels).not.toContain('insight.apply');
    expect(labels).toContain('insight.dismiss');
  });
});
