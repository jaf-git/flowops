// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { CanvasIndexEntry } from '../routes/CanvasIndexScreen';

import { ProcessRail } from './ProcessRail';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key}:${JSON.stringify(options)}`,
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

afterEach(cleanup);

const entries: CanvasIndexEntry[] = [
  { id: 'run-1', name: 'Integrare — Andrei', state: 'RUNNING', closed: 1, total: 4 },
  { id: 'run-2', name: 'Integrare — Maria', state: 'RUNNING', closed: 0, total: 2 },
  { id: 'run-3', name: 'Audit trimestrial', state: 'RUNNING', closed: 2, total: 2 },
];

function draw(current = 'run-2') {
  render(
    <MemoryRouter>
      <ProcessRail entries={entries} currentInstanceId={current} locale="ro" />
    </MemoryRouter>,
  );
}

describe('the process rail', () => {
  it('links every other process to its own plane', () => {
    draw();

    const hrefs = screen.getAllByRole('link').map((link) => link.getAttribute('href'));
    expect(hrefs).toEqual(['/ro/canvas/process/run-1', '/ro/canvas/process/run-3']);
  });

  it('shows the open process as where you are, not as somewhere to go', () => {
    draw();

    expect(screen.getByText('Integrare — Maria').closest('a')).toBeNull();
    expect(screen.getAllByRole('link')).toHaveLength(2);
  });

  it('carries no person and no per-person number', () => {
    draw();

    expect(document.body.textContent ?? '').not.toMatch(/assignee|held by|per person/i);
    expect(Object.keys(entries[0] as object)).not.toContain('assignee');
  });

  it('shows each process its own progress', () => {
    draw();

    expect(screen.queryByText('process.progress.label:{"closed":1,"total":4}')).not.toBeNull();
    expect(screen.queryByText('process.progress.label:{"closed":2,"total":2}')).not.toBeNull();
  });
});
