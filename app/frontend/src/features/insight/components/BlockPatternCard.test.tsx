// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { aDecision } from './insightFixtures';
import { BlockPatternCard } from './BlockPatternCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function blockPattern(over: Partial<Insight> = {}): Insight {
  return {
    kind: 'BLOCK_PATTERN',
    findingKey: 'k-1',
    action: null,
    subjectId: 't-1',
    subjectName: 'Client onboarding',
    stepTitle: 'Proposal',
    afterStep: null,
    beforeStep: null,
    occurrences: 7,
    runsTotal: 7,
    medianPhases: null,
    reason: 'waiting on finance for the cost basis',

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
    instances: ['a', 'b', 'c'],
    ...over,
  };
}

describe('BlockPatternCard', () => {
  it('names the step and how often it stopped', () => {
    render(<BlockPatternCard insight={blockPattern()} decision={aDecision()} />);

    const claim = screen.getByText(/insight\.blockPattern\.claim/);
    expect(claim.textContent).toContain('"step":"Proposal"');
    expect(claim.textContent).toContain('"count":7');
  });

  it('quotes the reason exactly as somebody typed it', () => {
    render(<BlockPatternCard insight={blockPattern()} decision={aDecision()} />);

    const quote = screen.getByText('waiting on finance for the cost basis');
    expect(quote.tagName.toLowerCase()).toBe('blockquote');
  });

  it('does not show the normalised form used for grouping', () => {
    const { container } = render(
      <BlockPatternCard
        insight={blockPattern({ reason: 'Waiting on FINANCE.' })}
        decision={aDecision()}
      />,
    );

    expect(container.textContent).toContain('Waiting on FINANCE.');
    expect(container.textContent).not.toContain('waiting on finance ');
  });

  it('carries its evidence', () => {
    render(<BlockPatternCard insight={blockPattern()} decision={aDecision()} />);

    expect(screen.getByText(/insight\.blockPattern\.evidence/).textContent).toContain('"count":7');
  });

  it('names nobody, even though the reason is a colleague own sentence', () => {
    const { container } = render(
      <BlockPatternCard insight={blockPattern()} decision={aDecision()} />,
    );

    expect(container.textContent).not.toMatch(/assignee|Andrei|Maria|Ionu/);
  });

  it('signals no urgency with colour, in either direction', () => {
    const { container } = render(
      <BlockPatternCard insight={blockPattern()} decision={aDecision()} />,
    );

    const card = container.querySelector('article');

    expect(card?.getAttribute('style')).not.toContain('var(--alert)');
    expect(card?.getAttribute('style')).not.toContain('var(--waiting)');
  });

  it('offers no Apply', () => {
    render(
      <BlockPatternCard insight={blockPattern()} decision={aDecision()} onExplain={vi.fn()} />,
    );

    const labels = screen.getAllByRole('button').map((button) => button.textContent);
    expect(labels).not.toContain('insight.apply');
    expect(labels).toContain('insight.dismiss');
  });
});
