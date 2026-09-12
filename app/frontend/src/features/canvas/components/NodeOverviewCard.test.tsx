// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { durationLabel } from '../../../shared/lib/elapsed';
import type { CanvasNode } from '../model/node';
import { NodeOverviewCard } from './NodeOverviewCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      return typeof phrase === 'string'
        ? phrase.replace(/{{(\w+)}}/g, (_, name: string) => String(values?.[name] ?? ''))
        : key;
    },
    i18n: { language: 'en' },
  }),
}));

const NOW = new Date('2026-08-13T09:00:00Z');
const HOUR = 60 * 60;

const LONG_TITLE = 'Reconcile the supplier ledger against the quarterly statement and file it';

function node(over: Partial<CanvasNode> = {}): CanvasNode {
  return {
    id: 's1',
    title: 'Crearea conturilor IT',
    condition: 'Assigned',
    stepNumber: 4,
    taskState: 'InProgress',
    assignee: { name: 'Dan Stan' },
    dueAt: '2026-08-20T09:00:00Z',
    phases: [{ phase: 'active', seconds: 2 * HOUR }],
    ...over,
  };
}

afterEach(cleanup);

describe('the overview card', () => {
  it('gives the whole title, which is what the node had to give up', () => {
    render(<NodeOverviewCard node={node({ title: LONG_TITLE })} now={NOW} />);

    const title = screen.getByTestId('overview-title');

    expect(title.textContent).toBe(LONG_TITLE);
    expect(title.style.webkitLineClamp).toBe('');
    expect(title.style.overflow).not.toBe('hidden');
  });

  it('shows the state, the person, the deadline and the time, from what it was handed', () => {
    render(<NodeOverviewCard node={node()} now={NOW} />);

    expect(screen.getByText(en.ui.taskState.InProgress)).toBeTruthy();
    expect(screen.getByText('Dan Stan')).toBeTruthy();
    expect(screen.getByTestId('overview-due')).toBeTruthy();
    expect(screen.getByTestId('overview-phases')).toBeTruthy();
  });

  it('asks the network for nothing when it is revealed', () => {
    const asked = vi.fn();
    const original = global.fetch;
    global.fetch = asked as unknown as typeof fetch;

    try {
      render(<NodeOverviewCard node={node()} now={NOW} />);
      expect(asked).not.toHaveBeenCalled();
    } finally {
      global.fetch = original;
    }
  });

  it('shows a blocked and overdue step as both, with neither masking the other', () => {
    render(
      <NodeOverviewCard
        node={node({
          taskState: 'Blocked',
          blockedReason: 'Se așteaptă reînnoirea licenței',
          dueAt: '2026-08-10T09:00:00Z',
        })}
        now={NOW}
      />,
    );

    expect(screen.getByText(en.ui.taskState.Blocked)).toBeTruthy();
    expect(screen.getByText(en.canvas.node.overdue)).toBeTruthy();
    expect(screen.getByTestId('overview-blocker').textContent).toBe(
      'Se așteaptă reînnoirea licenței',
    );
  });

  it('names the step and what it waits on, on a process graph', () => {
    render(
      <NodeOverviewCard
        node={node({
          condition: 'Pending',
          taskState: undefined,
          meta: [{ label: 'Waits on', value: 'Steps 3, 4' }],
        })}
        now={NOW}
      />,
    );

    expect(screen.getByTestId('overview-step').textContent).toBe('Step 4');
    expect(screen.getByTestId('overview-meta').textContent).toContain('Steps 3, 4');
  });

  it('gives a step nobody can start yet no clock and nobody’s name', () => {
    render(
      <NodeOverviewCard
        node={node({
          condition: 'Pending',
          taskState: undefined,
          assignee: { name: 'Dan Stan' },
          dueAt: '2026-08-10T09:00:00Z',
          phases: [{ phase: 'active', seconds: HOUR }],
        })}
        now={NOW}
      />,
    );

    expect(screen.queryByText('Dan Stan')).toBeNull();
    expect(screen.queryByTestId('overview-due')).toBeNull();
    expect(screen.queryByTestId('overview-phases')).toBeNull();
    expect(screen.queryByText(en.canvas.node.overdue)).toBeNull();
  });

  it('renders an erased assignee as a former member, with no name', () => {
    render(<NodeOverviewCard node={node({ assignee: { name: null, erased: true } })} now={NOW} />);

    expect(screen.getByText(en.ui.personChip.formerMember)).toBeTruthy();
    expect(screen.queryByText('Dan Stan')).toBeNull();
  });

  it('never prints a duration without the phase it belongs to', () => {
    render(
      <NodeOverviewCard
        node={node({
          phases: [
            { phase: 'active', seconds: 2 * HOUR },
            { phase: 'blocked', seconds: 72 * HOUR },
          ],
        })}
        now={NOW}
      />,
    );

    const phases = screen.getByTestId('overview-phases');

    expect(phases.textContent).toContain('work');
    expect(phases.textContent).toContain('blocked');

    const total = 2 * HOUR + 72 * HOUR;

    expect(phases.textContent).not.toContain(durationLabel(total, 'en'));
  });
});
