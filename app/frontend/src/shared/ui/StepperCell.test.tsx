// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { StepperCell } from './StepperCell';
import { segmentsFromProgress } from './stepperSegments';

afterEach(cleanup);

describe('segments from a progress count', () => {
  it('marks exactly the closed ones', () => {
    expect(segmentsFromProgress(3, 7).map((segment) => segment.condition)).toEqual([
      'CLOSED',
      'CLOSED',
      'CLOSED',
      'PENDING',
      'PENDING',
      'PENDING',
      'PENDING',
    ]);
  });

  it('handles both ends', () => {
    expect(segmentsFromProgress(0, 3).every((s) => s.condition === 'PENDING')).toBe(true);
    expect(segmentsFromProgress(3, 3).every((s) => s.condition === 'CLOSED')).toBe(true);
  });

  it('draws nothing for a run with no steps', () => {
    const { container } = render(
      <StepperCell segments={segmentsFromProgress(0, 0)} summary="nothing yet" />,
    );

    expect(container.firstChild).toBeNull();
  });
});

describe('the stepper', () => {
  it('announces the caller’s summary and hides the dots', () => {
    render(<StepperCell segments={segmentsFromProgress(2, 4)} summary="2 of 4 steps done" />);

    const cell = screen.getByRole('img', { name: '2 of 4 steps done' });
    expect(cell).toBeTruthy();
    expect(cell.querySelectorAll('[aria-hidden="true"]').length).toBe(4);
  });
});
