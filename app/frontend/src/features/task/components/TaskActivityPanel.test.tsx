// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ActivityEntry } from '../api/taskApi';
import { TaskActivityPanel } from './TaskActivityPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let entries: ActivityEntry[] = [];

vi.mock('../hooks/useTasks', () => ({
  useTaskActivity: () => ({ data: { entries }, isError: false, isPending: false }),
  useCommentOnTask: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    error: null,
  }),
}));

afterEach(() => {
  cleanup();
  entries = [];
});

function transition(
  to: string,
  at: string,
  options: { overridden?: boolean; reason?: string; from?: string | null } = {},
): ActivityEntry {
  return {
    kind: 'TRANSITION',
    occurredAt: at,
    actorId: 'a1',
    actorName: 'Ionuț Petrescu',
    from: (options.from ?? 'CREATED') as ActivityEntry['from'],
    to: to as ActivityEntry['to'],
    reason: options.reason ?? null,
    overridden: options.overridden ?? false,
    body: null,
  };
}

function comment(body: string, at: string, actorName = 'Andrei Munteanu'): ActivityEntry {
  return {
    kind: 'COMMENT',
    occurredAt: at,
    actorId: 'a2',
    actorName,
    from: null,
    to: null,
    reason: null,
    overridden: false,
    body,
  };
}

describe('the task activity', () => {
  it('renders moves and comments as one sequence, in the order the server gave them', () => {
    entries = [
      transition('CREATED', '2026-08-19T09:00:00Z', { from: null }),
      comment('Aștept oferta de la furnizor.', '2026-08-19T10:00:00Z'),
      transition('BLOCKED', '2026-08-19T11:00:00Z', {
        from: 'IN_PROGRESS',
        reason: 'Furnizorul nu răspunde.',
      }),
      comment('Am sunat de trei ori.', '2026-08-19T12:00:00Z'),
    ];

    render(<TaskActivityPanel taskId="t1" closed={false} />);

    const rows = screen.getAllByRole('listitem');
    expect(rows).toHaveLength(4);
    expect(rows[1]?.textContent).toContain('Aștept oferta de la furnizor.');
    expect(rows[2]?.textContent).toContain('Furnizorul nu răspunde.');
    expect(rows[3]?.textContent).toContain('Am sunat de trei ori.');
  });

  it('marks a forced move and leaves an ordinary one unmarked', () => {
    entries = [
      transition('ACCEPTED', '2026-08-19T09:00:00Z'),
      transition('CLOSED', '2026-08-19T10:00:00Z', {
        from: 'ACCEPTED',
        overridden: true,
        reason: 'Comanda a fost anulată.',
      }),
    ];

    render(<TaskActivityPanel taskId="t1" closed />);

    const marks = screen.getAllByText('task.activity.forced');
    expect(marks).toHaveLength(1);
    expect(screen.getAllByRole('listitem')[1]?.textContent).toContain('task.activity.forced');
  });

  it('shows an erased author as a former member and leaves their words alone', () => {
    entries = [comment('Întreabă-o pe Maria despre factură.', '2026-08-19T09:00:00Z', '')];

    render(<TaskActivityPanel taskId="t1" closed={false} />);

    expect(screen.getByText('task.formerMember')).toBeDefined();

    expect(screen.getByText('Întreabă-o pe Maria despre factură.')).toBeDefined();
  });

  it('offers no composer on a closed task, and offers no way to edit or remove a comment', () => {
    entries = [comment('O notă.', '2026-08-19T09:00:00Z')];

    render(<TaskActivityPanel taskId="t1" closed />);

    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('offers a composer on a task that is still live', () => {
    entries = [];

    render(<TaskActivityPanel taskId="t1" closed={false} />);

    expect(screen.getByRole('textbox')).toBeDefined();

    expect(screen.getByText('task.activity.empty')).toBeDefined();
  });
});
