// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { DeadlineIndicator } from './DeadlineIndicator';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) =>
      values === undefined
        ? key
        : `${key}(${Object.entries(values)
            .map(([name, value]) => `${name}=${value}`)
            .join(',')})`,
    i18n: { language: 'en' },
  }),
}));

const NOW = new Date('2026-08-03T12:00:00Z');
const IN_THREE_DAYS = '2026-08-06T12:00:00Z';
const TWO_DAYS_AGO = '2026-08-01T12:00:00Z';

afterEach(cleanup);

describe('the deadline indicator', () => {
  it('says when the work is due while it is still on time', () => {
    render(<DeadlineIndicator dueAt={IN_THREE_DAYS} now={NOW} />);

    expect(screen.getByText(/ui\.deadline\.on-time/)).toBeDefined();
  });

  it('takes at-risk from the caller rather than deriving it from the deadline', () => {
    render(<DeadlineIndicator dueAt={IN_THREE_DAYS} atRisk now={NOW} />);

    expect(screen.getByText(/ui\.deadline\.at-risk/)).toBeDefined();
  });

  it('says how far past, not merely that it is past', () => {
    render(<DeadlineIndicator dueAt={TWO_DAYS_AGO} now={NOW} />);

    expect(screen.getByText(/ui\.deadline\.overdue\(.*amount=2d/)).toBeDefined();
  });

  it('renders blocked and overdue together, with neither replacing the other', () => {
    render(
      <DeadlineIndicator dueAt={TWO_DAYS_AGO} blockedOn="waiting on the supplier" now={NOW} />,
    );

    expect(screen.getByText(/ui\.deadline\.overdue/)).toBeDefined();
    expect(screen.getByText(/reason=waiting on the supplier/)).toBeDefined();
  });

  it('gives blocked a different treatment from overdue', () => {
    render(
      <DeadlineIndicator dueAt={TWO_DAYS_AGO} blockedOn="waiting on the supplier" now={NOW} />,
    );

    const overdue = screen.getByText(/ui\.deadline\.overdue/).closest('span.ui-chip');
    const blocked = screen.getByText(/ui\.deadline\.blocked/).closest('span.ui-chip');

    expect(overdue?.className).not.toBe(blocked?.className);
    expect(blocked?.className).toContain('ui-chip-blocked');
  });

  it('says nothing rather than something wrong about work with no date', () => {
    render(<DeadlineIndicator dueAt={null} now={NOW} />);

    expect(screen.getByText('—')).toBeTruthy();
    expect(screen.queryByText(/ui\.deadline\.overdue/)).toBeNull();
    expect(screen.queryByText(/ui\.deadline\.atRisk/)).toBeNull();
  });
});
