// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { ProgressMeter } from './ProgressMeter';

afterEach(cleanup);

describe('the progress meter', () => {
  it('announces the whole sentence it was given', () => {
    render(<ProgressMeter done={2} total={5} label="2 din 5 pași închiși" />);

    expect(screen.getByRole('progressbar').getAttribute('aria-label')).toBe('2 din 5 pași închiși');
  });

  it('carries the numbers a screen reader reads from the role', () => {
    render(<ProgressMeter done={2} total={5} label="2 din 5" />);

    const bar = screen.getByRole('progressbar');
    expect(bar.getAttribute('aria-valuenow')).toBe('2');
    expect(bar.getAttribute('aria-valuemax')).toBe('5');
  });

  it('renders nothing when there is nothing to measure', () => {
    const { container } = render(<ProgressMeter done={0} total={0} label="—" />);

    expect(container.innerHTML).toBe('');
  });

  it('does not draw past the end when more are done than exist', () => {
    render(<ProgressMeter done={9} total={5} label="9 din 5" />);

    const fill = screen.getByRole('progressbar').firstElementChild as HTMLElement;
    expect(fill.style.width).toBe('100%');
  });
});
