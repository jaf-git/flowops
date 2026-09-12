// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { galleryEntries } from '../model/gallery';
import { NodeGalleryScreen } from './NodeGalleryScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      if (typeof phrase !== 'string') {
        return key;
      }

      return phrase.replace(/{{(\w+)}}/g, (_, name: string) => values?.[name] ?? '');
    },
    i18n: { language: 'en' },
  }),
}));

const FIXTURE_PEOPLE = galleryEntries((key) => key)
  .map((entry) => entry.node.assignee?.name)
  .filter((name): name is string => name !== null && name !== undefined);

afterEach(cleanup);

describe('the node gallery', () => {
  it('draws every state the visual contract names', () => {
    const entries = galleryEntries((key) => key);
    render(<NodeGalleryScreen />);

    expect(screen.getAllByRole('button', { name: /./ }).length).toBeGreaterThanOrEqual(
      entries.length,
    );

    for (const entry of entries) {
      expect(screen.getByTestId(`gallery-${entry.node.id}`)).toBeTruthy();
    }
  });

  it('takes every word on it from the translation resources', () => {
    const untranslated = galleryEntries((key) => key).flatMap((entry) =>
      [
        entry.note,
        entry.node.title,
        entry.node.blockedReason,
        entry.node.reworkNote,
        ...(entry.node.meta ?? []).flatMap((row) => [row.label, row.value]),
      ].filter((text): text is string => text !== undefined && !text.startsWith('canvas.')),
    );

    expect(untranslated).toEqual([]);
  });

  it('says plainly that the work on it is invented', () => {
    render(<NodeGalleryScreen />);

    expect(screen.getByText(en.canvas.gallery.lead)).toBeTruthy();
  });

  it('carries the plane’s single call to action exactly once', () => {
    render(<NodeGalleryScreen />);

    expect(screen.getAllByRole('button', { name: en.canvas.node.assign })).toHaveLength(1);
  });

  it('says the press did nothing, rather than letting it do nothing quietly', () => {
    render(<NodeGalleryScreen />);

    expect(screen.getByTestId('canvas-inert-notice').textContent).toBe('');

    fireEvent.click(screen.getByRole('button', { name: en.canvas.node.assign }));

    expect(screen.getByTestId('canvas-inert-notice').textContent).toBe(en.canvas.gallery.inert);
  });

  it('shows a blocked and an overdue node whose treatments differ', () => {
    render(<NodeGalleryScreen />);

    const blocked = screen.getByTestId('gallery-blocked').querySelector('.canvas-node');
    const overdue = screen.getByTestId('gallery-overdue').querySelector('.canvas-node');

    expect((blocked as HTMLElement).style.borderColor).not.toBe(
      (overdue as HTMLElement).style.borderColor,
    );
  });

  it('names a person only through the person chip, never in a labelled row', () => {
    const offenders = galleryEntries((key) => key)
      .flatMap((entry) => entry.node.meta ?? [])
      .filter((row) => FIXTURE_PEOPLE.some((name) => `${row.label} ${row.value}`.includes(name)));

    expect(offenders).toEqual([]);
  });

  it('renders every figure it shows inside a region entitled to carry one', () => {
    const { container } = render(<NodeGalleryScreen />);

    const MAY_CARRY_A_FIGURE = [
      'canvas-node-step',
      'canvas-node-title',
      'canvas-node-meta-row',
      'canvas-node-blocker',
      'canvas-node-note',
      'canvas-node-phases',
    ]
      .map((region) => `[data-testid="${region}"]`)
      .join(',');

    const offenders: string[] = [];

    for (const node of container.querySelectorAll('.canvas-node')) {
      for (const element of node.querySelectorAll('*')) {
        const text = element.textContent ?? '';

        if (element.children.length === 0 && /\d/.test(text)) {
          if (element.closest(MAY_CARRY_A_FIGURE) === null) {
            offenders.push(text);
          }
        }
      }
    }

    expect(offenders).toEqual([]);
  });

  it('never prints a duration without the phase it belongs to', () => {
    const { container } = render(<NodeGalleryScreen />);

    const phrases = [...container.querySelectorAll('.fo-phase')].map(
      (element) => element.textContent ?? '',
    );

    expect(phrases.length).toBeGreaterThan(0);

    for (const phrase of phrases) {
      const named = ['work', 'blocked', 'waiting', 'in review', 'awaiting approval'].some((phase) =>
        phrase.includes(phase),
      );

      expect(named, `"${phrase}" prints a duration with no phase`).toBe(true);
    }
  });
});
