// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { i18next } from '../../i18n';
import { RelativeTime } from './RelativeTime';

describe('RelativeTime', () => {
  const now = new Date('2026-08-03T12:00:00Z');

  afterEach(() => {
    cleanup();
    void i18next.changeLanguage('en');
  });

  it('says how long ago an instant was, in words', () => {
    render(<RelativeTime value="2026-07-31T12:00:00Z" now={now} />);

    expect(screen.getByText('3 days ago')).toBeDefined();
  });

  it('chooses the largest whole unit, so nothing reads as 49 hours ago', () => {
    render(<RelativeTime value="2026-08-01T11:00:00Z" now={now} />);

    expect(screen.getByText('2 days ago')).toBeDefined();
  });

  it('reads a future instant as future rather than as a negative past', () => {
    render(<RelativeTime value="2026-08-06T12:00:00Z" now={now} />);

    expect(screen.getByText('in 3 days')).toBeDefined();
  });

  it('carries the exact instant in a machine-readable attribute', () => {
    const { container } = render(<RelativeTime value="2026-07-31T12:00:00Z" now={now} />);

    const element = container.querySelector('time');

    expect(element?.getAttribute('dateTime')).toBe('2026-07-31T12:00:00.000Z');
  });

  it('renders what the server sent when the value cannot be parsed, never "Invalid Date"', () => {
    render(<RelativeTime value="not-a-date" now={now} />);

    expect(screen.getByText('not-a-date')).toBeDefined();
    expect(screen.queryByText(/Invalid Date/)).toBeNull();
  });
});
