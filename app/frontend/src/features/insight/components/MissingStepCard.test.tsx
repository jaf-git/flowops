// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { aDecision } from './insightFixtures';
import { monthsBetween } from '../model/window';
import { MissingStepCard } from './MissingStepCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function insight(over: Partial<Insight> = {}): Insight {
  return {
    kind: 'MISSING_STEP',
    findingKey: 'k-1',
    action: null,
    subjectId: '11111111-1111-1111-1111-111111111111',
    subjectName: 'Client onboarding',
    stepTitle: 'Send reminder',
    afterStep: 'Kickoff',
    beforeStep: null,
    occurrences: 8,
    runsTotal: 12,
    medianPhases: null,
    reason: null,

    dependsOnStep: null,
    daysSinceLastUse: null,
    windowDays: null,
    windowFrom: '2026-04-06T08:00:00Z',
    windowTo: '2026-08-05T07:00:00Z',

    expectedIntervalDays: null,
    estimatedMs: null,
    medianWorkMs: null,
    fastestMiddleMs: null,
    slowestMiddleMs: null,
    spreadIsWide: null,
    excludedRuns: null,
    qualifyingPopulation: null,
    instances: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'],
    ...over,
  };
}

describe('MissingStepCard', () => {
  it('states the count, the denominator and the step, in that order', () => {
    render(<MissingStepCard insight={insight()} decision={aDecision()} />);

    const claim = screen.getByText(/insight\.missingStep\.claim/);
    expect(claim.textContent).toContain('"added":8');
    expect(claim.textContent).toContain('"count":12');
    expect(claim.textContent).toContain('"step":"Send reminder"');
  });

  it('always carries its evidence', () => {
    render(<MissingStepCard insight={insight()} decision={aDecision()} />);

    const evidence = screen.getByText(/insight\.missingStep\.evidence/);
    expect(evidence.textContent).toContain('"count":12');
    expect(evidence.textContent).toContain('"months":4');
  });

  it('names the planned step it follows', () => {
    render(<MissingStepCard insight={insight({ afterStep: 'Proposal' })} decision={aDecision()} />);

    expect(screen.getByText(/insight\.missingStep\.after/).textContent).toContain(
      '"step":"Proposal"',
    );
  });

  it('says so plainly when it lands at the very start rather than printing an empty neighbour', () => {
    render(<MissingStepCard insight={insight({ afterStep: null })} decision={aDecision()} />);

    expect(screen.getByText(/insight\.missingStep\.atTheStart/)).toBeTruthy();
    expect(screen.queryByText(/insight\.missingStep\.after\b/)).toBeNull();
  });

  it('signals no urgency with colour, in either direction', () => {
    const { container } = render(<MissingStepCard insight={insight()} decision={aDecision()} />);

    const card = container.querySelector('article');

    expect(card?.getAttribute('style')).not.toContain('var(--alert)');
    expect(card?.getAttribute('style')).not.toContain('var(--waiting)');
  });

  it('names nobody', () => {
    const { container } = render(
      <MissingStepCard
        insight={insight({ subjectName: 'Client onboarding' })}
        decision={aDecision()}
      />,
    );

    expect(container.textContent).not.toMatch(/assignee|Andrei|Maria|Ionu/);
  });

  it('says the evidence is being fetched rather than appearing inert', () => {
    render(
      <MissingStepCard insight={insight()} decision={aDecision()} onExplain={vi.fn()} explaining />,
    );

    const button = screen
      .getAllByRole('button')
      .find((each) => each.textContent?.includes('insight.explaining')) as HTMLButtonElement;
    expect(button).toBeTruthy();
    expect((button as HTMLButtonElement).disabled).toBe(true);
  });

  it('says so when the evidence could not be fetched', () => {
    render(
      <MissingStepCard
        insight={insight()}
        decision={aDecision()}
        onExplain={vi.fn()}
        explainFailed
      />,
    );

    expect(screen.getByRole('alert').textContent).toContain('insight.explainFailed');
  });

  it('offers Explain only where the reader was given a way to take one', async () => {
    const explain = vi.fn();
    const { rerender } = render(
      <MissingStepCard insight={insight()} decision={aDecision()} onExplain={explain} />,
    );

    await userEvent.click(screen.getByRole('button', { name: /insight\.explain/ }));
    expect(explain).toHaveBeenCalledTimes(1);

    rerender(<MissingStepCard insight={insight()} decision={aDecision()} />);
    expect(screen.queryByRole('button', { name: /insight\.explain/ })).toBeNull();
  });
});

describe('monthsBetween', () => {
  it('counts whole months across the window', () => {
    expect(monthsBetween('2026-04-06T00:00:00Z', '2026-08-05T00:00:00Z')).toBe(4);
  });

  it('never says zero months', () => {
    expect(monthsBetween('2026-04-06T00:00:00Z', '2026-04-06T04:00:00Z')).toBe(1);
  });

  it('falls back to one rather than NaN when the window is absent or unreadable', () => {
    expect(monthsBetween(null, '2026-08-05T00:00:00Z')).toBe(1);
    expect(monthsBetween('2026-04-06T00:00:00Z', null)).toBe(1);
    expect(monthsBetween('not-a-date', '2026-08-05T00:00:00Z')).toBe(1);
  });
});
