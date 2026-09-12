// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import type { JSX } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { CanvasIndexScreen, type CanvasIndexEntry } from './CanvasIndexScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key}:${JSON.stringify(options)}`,
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

afterEach(cleanup);

function draw(
  entries: readonly CanvasIndexEntry[],
  state: { isPending?: boolean; isError?: boolean } = {},
): void {
  render(
    <MemoryRouter>
      <CanvasIndexScreen
        locale="en"
        entries={entries}
        isPending={state.isPending ?? false}
        isError={state.isError ?? false}
      />
    </MemoryRouter>,
  ) as unknown as JSX.Element;
}

const run = (over: Partial<CanvasIndexEntry> = {}): CanvasIndexEntry => ({
  id: 'run-1',
  name: 'Integrare — Andrei',
  state: 'RUNNING',
  closed: 1,
  total: 4,
  ...over,
});

describe('the canvas index', () => {
  it('links every process it lists to the plane for that process', () => {
    draw([run(), run({ id: 'run-2', name: 'Integrare — Maria' })]);

    const links = screen.getAllByText('canvas.index.open');
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/en/canvas/process/run-1',
      '/en/canvas/process/run-2',
    ]);
  });

  it('renders exactly what it is given, adding no rule of its own', () => {
    draw([run()]);

    expect(screen.getAllByText('canvas.index.open')).toHaveLength(1);
    expect(screen.queryByText('Integrare — Andrei')).not.toBeNull();
  });

  it('shows no person and no per-person number', () => {
    draw([run(), run({ id: 'run-2', name: 'Integrare — Maria' })]);

    const rendered = document.body.textContent ?? '';
    expect(rendered).not.toMatch(/assignee|Ioana|Andrei Popescu/i);
    expect(Object.keys(run())).not.toContain('assignee');
  });

  it('is calm when nothing is running', () => {
    draw([]);

    expect(screen.queryByText('canvas.index.empty.heading')).not.toBeNull();
    expect(screen.queryByText('canvas.index.loadFailed')).toBeNull();
  });

  it('distinguishes a failed read from an empty one', () => {
    draw([], { isError: true });

    expect(screen.queryByText('canvas.index.loadFailed')).not.toBeNull();
    expect(screen.queryByText('canvas.index.empty.heading')).toBeNull();
  });
});
