// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Insight } from '../api/insightApi';
import { UnusedTemplateCard } from './UnusedTemplateCard';
import { aDecision } from './insightFixtures';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function unused(over: Partial<Insight> = {}): Insight {
  return {
    kind: 'UNUSED_TEMPLATE',
    findingKey: 'unused',
    action: 'RETIRE_TEMPLATE',
    subjectId: 't-9',
    subjectName: 'Pregătire audit trimestrial',
    stepTitle: '',
    afterStep: null,
    beforeStep: null,
    occurrences: 4,
    runsTotal: 90,
    medianPhases: null,
    reason: null,
    dependsOnStep: null,
    daysSinceLastUse: 180,
    windowDays: 90,
    windowFrom: '2026-02-21T08:00:00Z',
    windowTo: null,

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

describe('UnusedTemplateCard', () => {
  it('names the template and how long it has been quiet', () => {
    render(<UnusedTemplateCard insight={unused()} decision={aDecision()} />);

    const claim = screen.getByText(/insight\.unusedTemplate\.claim\b/);
    expect(claim.textContent).toContain('"name":"Pregătire audit trimestrial"');
    expect(claim.textContent).toContain('"count":180');
  });

  it('says never used rather than reporting a negative number of days', () => {
    render(
      <UnusedTemplateCard insight={unused({ daysSinceLastUse: -1 })} decision={aDecision()} />,
    );

    expect(screen.getByText(/insight\.unusedTemplate\.claimNeverUsed/)).toBeTruthy();
    expect(screen.queryByText(/"count":-1/)).toBeNull();
  });

  it('states the window it judged against', () => {
    render(<UnusedTemplateCard insight={unused()} decision={aDecision()} />);

    expect(screen.getByText(/insight\.unusedTemplate\.evidence/).textContent).toContain(
      '"window":90',
    );
  });

  it('names the limitation it cannot see past', () => {
    render(<UnusedTemplateCard insight={unused()} decision={aDecision()} />);

    expect(screen.getByText('insight.unusedTemplate.seasonal')).toBeTruthy();
  });

  it('offers Apply, which retires and never deletes', () => {
    render(<UnusedTemplateCard insight={unused()} decision={aDecision()} />);

    expect(screen.getByText('insight.apply')).toBeTruthy();
    expect(screen.getByText('insight.dismiss')).toBeTruthy();
  });

  it('offers no Explain, because there is no scoped export for a task template', () => {
    render(<UnusedTemplateCard insight={unused()} decision={aDecision()} />);

    expect(screen.queryByText('insight.explain')).toBeNull();
  });

  it('signals no urgency with colour, in either direction', () => {
    const { container } = render(<UnusedTemplateCard insight={unused()} decision={aDecision()} />);

    const style = container.querySelector('article')?.getAttribute('style');
    expect(style).not.toContain('var(--alert)');
    expect(style).not.toContain('var(--waiting)');
  });
});
