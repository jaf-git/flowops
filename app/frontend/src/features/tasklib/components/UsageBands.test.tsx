// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import type { JSX } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { TemplateUsage } from '../api/taskTemplateApi';
import { UsageBands } from './UsageBands';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

const noTasks = (): JSX.Element => <div data-testid="rows" />;

function usage(overrides: Partial<TemplateUsage> = {}): TemplateUsage {
  return {
    stamped: 12,
    estimatedHours: 2,
    medianActiveSeconds: 14400,
    lowerQuartileSeconds: 12600,
    upperQuartileSeconds: 16200,
    measuredTasks: 9,
    tooFewToAverage: false,
    varyWidely: false,
    passedFirstTime: 6,
    reviewed: 8,
    live: { notStarted: 2, running: 3, blocked: 1, inReview: 1, finished: 5, overdue: 2 },
    ...overrides,
  };
}

describe('UsageBands', () => {
  it('says a template is not yet used rather than drawing a page of zeroes', () => {
    render(<UsageBands renderTasks={noTasks} usage={usage({ stamped: 0 })} />);

    expect(screen.getByTestId('never-used')).toBeTruthy();
    expect(screen.queryByText(/tasklib\.usage\.duration\.heading/)).toBeNull();
    expect(screen.queryByText(/tasklib\.usage\.approval\.heading/)).toBeNull();
  });

  it('marks a thin sample rather than hiding it, and still shows the figures', () => {
    render(
      <UsageBands
        renderTasks={noTasks}
        usage={usage({ measuredTasks: 2, tooFewToAverage: true })}
      />,
    );

    expect(screen.getByTestId('thin-sample').textContent).toContain('"count":2');
    expect(screen.getByText(/ui\.phase\.active/)).toBeTruthy();

    expect(screen.queryByText(/tasklib\.usage\.duration\.middleHalf/)).toBeNull();
  });

  it('leads with the spread when the durations vary too widely for one figure', () => {
    render(
      <UsageBands
        renderTasks={noTasks}
        usage={usage({ varyWidely: true, lowerQuartileSeconds: 7200, upperQuartileSeconds: 43200 })}
      />,
    );

    expect(screen.getByText(/tasklib\.usage\.duration\.varyWidely/)).toBeTruthy();

    expect(screen.getByText(/tasklib\.usage\.duration\.middleValue/)).toBeTruthy();
  });

  it('invents no duration for a template whose tasks nobody has finished', () => {
    render(
      <UsageBands
        renderTasks={noTasks}
        usage={usage({
          measuredTasks: 0,
          tooFewToAverage: true,
          medianActiveSeconds: null,
          lowerQuartileSeconds: null,
          upperQuartileSeconds: null,
        })}
      />,
    );

    expect(screen.getByText(/tasklib\.usage\.duration\.nothingMeasured/)).toBeTruthy();
    expect(screen.queryByTestId('thin-sample')).toBeNull();
  });

  it('reports first-try approval as two counts and never as a rate', () => {
    const { container } = render(<UsageBands renderTasks={noTasks} usage={usage()} />);

    const figure = screen.getByTestId('first-try');
    expect(figure.textContent).toContain('"passed":6');
    expect(figure.textContent).toContain('"reviewed":8');

    expect(container.textContent).not.toContain('%');
  });

  it('says nothing has been reviewed rather than reporting nought out of nought', () => {
    render(<UsageBands renderTasks={noTasks} usage={usage({ passedFirstTime: 0, reviewed: 0 })} />);

    expect(screen.getByText(/tasklib\.usage\.approval\.noneReviewed/)).toBeTruthy();
    expect(screen.queryByTestId('first-try')).toBeNull();
  });
});
