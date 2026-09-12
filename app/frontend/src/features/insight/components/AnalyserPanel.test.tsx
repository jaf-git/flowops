// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { AnalyserLine, AnalyserRun } from '../api/analyserApi';
import { AnalyserPanel } from './AnalyserPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function analyser(over: Partial<AnalyserLine> = {}): AnalyserLine {
  return {
    id: 'S6_LIBRARY',
    itemsRead: 63,
    findings: 0,
    failure: null,
    absences: [],
    clean: [],
    preconditions: [],
    ...over,
  };
}

function run(lines: readonly AnalyserLine[]): AnalyserRun {
  return {
    runId: 'r-1',
    windowFrom: '2026-06-01T00:00:00Z',
    windowTo: '2026-09-01T00:00:00Z',
    findingsTotal: lines.reduce((total, line) => total + line.findings, 0),
    failure: null,
    analysers: lines,
  };
}

const noop = (): void => {};

describe('the pipeline panel shows what each analyser said, not only what it found', () => {
  it('renders a clean result rather than leaving the analyser blank', () => {
    render(
      <AnalyserPanel
        run={run([
          analyser({
            clean: [
              {
                what: 'every_template_matched',
                detail: '18 approved templates checked; every one has matched work.',
              },
            ],
          }),
        ])}
        onRun={noop}
        running={false}
      />,
    );

    expect(
      screen.getByText('18 approved templates checked; every one has matched work.'),
    ).toBeTruthy();
  });

  it('renders what an analyser could not see', () => {
    render(
      <AnalyserPanel
        run={run([
          analyser({
            findings: 3,
            absences: [
              {
                what: 'intra_department_process',
                detail: 'Processes where every step is done by the same team are not detected yet.',
                blocking: false,
              },
            ],
          }),
        ])}
        onRun={noop}
        running={false}
      />,
    );

    expect(
      screen.getByText('Processes where every step is done by the same team are not detected yet.'),
    ).toBeTruthy();
  });

  it('renders an unmet precondition as a result rather than as silence', () => {
    render(
      <AnalyserPanel
        run={run([
          analyser({
            itemsRead: 0,
            preconditions: [
              {
                needed: 'at least 3 finished engagements',
                had: 'you have 0',
                met: false,
                remedy: 'Close an engagement',
              },
            ],
          }),
        ])}
        onRun={noop}
        running={false}
      />,
    );

    expect(screen.getByText(/at least 3 finished engagements/)).toBeTruthy();
    expect(screen.getByText(/you have 0/)).toBeTruthy();
    expect(screen.getByText(/Close an engagement/)).toBeTruthy();
  });

  it('says so when an analyser produced nothing at all', () => {
    render(
      <AnalyserPanel run={run([analyser({ itemsRead: 348 })])} onRun={noop} running={false} />,
    );

    expect(screen.getByText(/pipeline\.analysers\.silentButLooked/)).toBeTruthy();
  });

  it('distinguishes silence over evidence from silence over nothing', () => {
    render(<AnalyserPanel run={run([analyser({ itemsRead: 0 })])} onRun={noop} running={false} />);

    expect(screen.getByText('pipeline.analysers.silentAndBlind')).toBeTruthy();
    expect(screen.queryByText(/silentButLooked/)).toBeNull();
  });

  it('shows how much each analyser read, even when it found nothing', () => {
    render(
      <AnalyserPanel run={run([analyser({ itemsRead: 400 })])} onRun={noop} running={false} />,
    );

    expect(screen.getByText(/pipeline\.analysers\.read \{"count":400\}/)).toBeTruthy();
  });
});
