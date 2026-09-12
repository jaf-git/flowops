// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ConnectionIndicator } from './ConnectionIndicator';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function glyphOf(container: HTMLElement): string {
  const glyph = container.querySelector('[data-glyph]');
  return glyph?.getAttribute('data-glyph') ?? '';
}

describe('the connection indicator', () => {
  it('says which of the four things the stream is, in a word', () => {
    for (const status of ['connecting', 'live', 'reconnecting', 'offline'] as const) {
      const { unmount } = render(<ConnectionIndicator status={status} />);

      expect(screen.getByText(`ui.connection.${status}`)).toBeDefined();
      unmount();
    }
  });

  it('carries a shape as well as a colour, and a different one per state', () => {
    const glyphs = (['connecting', 'live', 'reconnecting', 'offline'] as const).map((status) => {
      const { container, unmount } = render(<ConnectionIndicator status={status} />);
      const glyph = glyphOf(container);
      unmount();
      return glyph;
    });

    expect(glyphs.every((glyph) => glyph !== '')).toBe(true);
    expect(new Set(glyphs).size).toBe(4);
  });

  it('hides the shape from assistive technology, since the word already carries the meaning', () => {
    const { container } = render(<ConnectionIndicator status="live" />);

    expect(container.querySelector('[data-glyph]')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('announces a change without stealing focus', () => {
    render(<ConnectionIndicator status="reconnecting" />);

    const region = screen.getByRole('status');

    expect(region.getAttribute('aria-live')).toBe('polite');
  });

  it('names what it is reporting on, so the announcement is not a bare word', () => {
    render(<ConnectionIndicator status="reconnecting" />);

    expect(screen.getByRole('status').textContent).toContain('ui.connection.label');
  });

  it('keeps the name out of the way of somebody reading the screen', () => {
    const { container } = render(<ConnectionIndicator status="live" />);

    const name = container.querySelector('.sr-only');
    expect(name?.textContent).toContain('ui.connection.label');
  });
});
