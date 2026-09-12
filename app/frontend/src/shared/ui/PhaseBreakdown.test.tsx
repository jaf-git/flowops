// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../i18n/locales/en/common.json';
import { PhaseBreakdown, type PhaseSpan } from './PhaseBreakdown';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          { ui: en.ui },
        );

      if (typeof phrase !== 'string') {
        return key;
      }

      return phrase.replace(/{{(\w+)}}/g, (_, name: string) => values?.[name] ?? '');
    },
    i18n: { language: 'en' },
  }),
}));

const HOUR = 60 * 60;
const DAY = 24 * HOUR;

afterEach(cleanup);

describe('the phase breakdown', () => {
  it('names the phase beside every duration it prints', () => {
    render(
      <PhaseBreakdown
        spans={[
          { phase: 'active', seconds: 4 * HOUR },
          { phase: 'blocked', seconds: 2 * DAY },
          { phase: 'wait', seconds: 1 * DAY },
        ]}
      />,
    );

    expect(screen.getByLabelText('Time by phase').textContent).toBe(
      '4h work · 2d blocked · 1d waiting',
    );
  });

  it('leaves no number standing on its own', () => {
    render(
      <PhaseBreakdown
        spans={[
          { phase: 'active', seconds: 90 * 60 },
          { phase: 'review', seconds: 3 * DAY },
          { phase: 'approval', seconds: 2 * HOUR },
        ]}
      />,
    );

    const segments = screen
      .getByLabelText('Time by phase')
      .textContent!.split('·')
      .map((segment) => segment.trim());

    expect(segments).toHaveLength(3);

    for (const segment of segments) {
      expect(segment, `"${segment}" is a duration with nothing qualifying it`).toMatch(
        /\d.*\p{L}{3,}/u,
      );
    }
  });

  it('sums a phase entered more than once', () => {
    const spans: PhaseSpan[] = [
      { phase: 'active', seconds: 3 * HOUR },
      { phase: 'blocked', seconds: 1 * DAY },
      { phase: 'active', seconds: 5 * HOUR },
    ];

    render(<PhaseBreakdown spans={spans} />);

    expect(screen.getByLabelText('Time by phase').textContent).toBe('8h work · 1d blocked');
  });

  it('omits a phase with nothing in it rather than printing zero', () => {
    render(
      <PhaseBreakdown
        spans={[
          { phase: 'active', seconds: 2 * HOUR },
          { phase: 'blocked', seconds: 0 },
        ]}
      />,
    );

    expect(screen.getByLabelText('Time by phase').textContent).toBe('2h work');
    expect(screen.queryByText(/blocked/)).toBeNull();
  });

  it('says so when no time has been recorded', () => {
    render(<PhaseBreakdown spans={[]} />);

    expect(screen.getByText('No time recorded yet')).toBeDefined();
  });
});

describe('the phase breakdown, drawn', () => {
  const spans: PhaseSpan[] = [
    { phase: 'active', seconds: 1 * HOUR },
    { phase: 'blocked', seconds: 3 * HOUR },
  ];

  it('keeps every word it prints without the bar', () => {
    const { container } = render(<PhaseBreakdown spans={spans} variant="bar" />);

    expect(container.textContent).toContain('1h work');
    expect(container.textContent).toContain('3h blocked');
  });

  it('draws one segment per phase and no segment for a phase with nothing in it', () => {
    render(<PhaseBreakdown spans={[...spans, { phase: 'wait', seconds: 0 }]} variant="bar" />);

    expect(screen.getAllByTestId('phase-segment')).toHaveLength(2);
  });

  it('sizes each segment by its share, so the drawing agrees with the words', () => {
    render(<PhaseBreakdown spans={spans} variant="bar" />);

    const widths = screen.getAllByTestId('phase-segment').map((segment) => segment.style.width);

    expect(widths).toEqual(['25%', '75%']);
  });

  it('gives each phase its own fill, so two phases never read as one', () => {
    render(<PhaseBreakdown spans={spans} variant="bar" />);

    const fills = screen.getAllByTestId('phase-segment').map((segment) => segment.style.background);

    expect(new Set(fills).size).toBe(fills.length);
  });

  it('hides the drawing from assistive technology, because the words are the content', () => {
    render(<PhaseBreakdown spans={spans} variant="bar" />);

    const [first] = screen.getAllByTestId('phase-segment');

    expect(first?.closest('[aria-hidden="true"]')).toBeTruthy();

    expect(screen.getByLabelText('Time by phase').textContent).toContain('1h work');
  });

  it('says nothing was recorded rather than drawing an empty bar', () => {
    render(<PhaseBreakdown spans={[]} variant="bar" />);

    expect(screen.queryAllByTestId('phase-segment')).toHaveLength(0);
    expect(screen.getByText('No time recorded yet')).toBeDefined();
  });
});
