// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { CanvasNode } from '../model/node';
import { TaskNodeCard } from './TaskNodeCard';

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

const HOUR = 60 * 60;
const NOW = new Date('2026-08-13T09:00:00Z');

const pendingStep: CanvasNode = {
  id: 'step-6',
  title: 'First-day briefing',
  condition: 'Pending',
  stepNumber: 6,
  meta: [{ label: 'Waits on', value: 'Steps 3, 4' }],
};

const blockedAndOverdue: CanvasNode = {
  id: 'step-4',
  title: 'Create IT accounts',
  condition: 'Assigned',
  stepNumber: 4,
  taskState: 'Blocked',
  assignee: { name: 'Dan Stan' },
  dueAt: '2026-08-12T09:00:00Z',
  blockedReason: 'Waiting on the licence renewal from the supplier',
  phases: [
    { phase: 'active', seconds: 2 * HOUR },
    { phase: 'blocked', seconds: 72 * HOUR },
  ],
};

afterEach(cleanup);

describe('a node on a canvas', () => {
  it('renders the header and the status band and nothing else it has no facts for', () => {
    render(<TaskNodeCard node={{ id: 'a', title: 'Probation review', condition: 'Pending' }} />);

    expect(screen.getByText('Probation review')).toBeTruthy();
    expect(screen.getByText(en.canvas.condition.Pending)).toBeTruthy();

    expect(screen.queryByTestId('canvas-node-blocker')).toBeNull();
    expect(screen.queryByTestId('canvas-node-meta')).toBeNull();
    expect(screen.queryByTestId('canvas-node-phases')).toBeNull();
    expect(screen.queryByRole('button', { name: /assign/i })).toBeNull();
  });

  it('numbers the step it belongs to', () => {
    render(<TaskNodeCard node={pendingStep} />);

    expect(screen.getByText('Step 6')).toBeTruthy();
  });

  it('marks its state with an icon as well as a word', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    const glyph = screen.getByTestId('canvas-node-glyph');

    expect(glyph.querySelector('svg')).toBeTruthy();
  });

  it('carries no step number on a board where a node is a task rather than a step', () => {
    render(<TaskNodeCard node={{ id: 'a', title: 'Draft the offer', condition: 'Assigned' }} />);

    expect(screen.queryByText(/^STEP/)).toBeNull();
  });

  it('names the person holding the work', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    expect(screen.getByText('Dan Stan')).toBeTruthy();
  });

  it('renders an erased assignee as a former member, with no name and no initials', () => {
    render(
      <TaskNodeCard
        node={{ ...blockedAndOverdue, assignee: { name: null, erased: true } }}
        now={NOW}
      />,
    );

    expect(screen.getByText(en.ui.personChip.formerMember)).toBeTruthy();
    expect(screen.queryByText('Dan Stan')).toBeNull();
  });

  it('renders the blocker reason whole, because a truncated one cannot be acted on', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    const strip = screen.getByTestId('canvas-node-blocker');
    expect(strip.textContent).toContain('Waiting on the licence renewal from the supplier');

    expect(strip.style.textOverflow).not.toBe('ellipsis');
    expect(strip.style.whiteSpace).not.toBe('nowrap');
  });

  it('states every duration with the phase it belongs to, and no total', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    const phases = screen.getByTestId('canvas-node-phases');
    expect(phases.textContent).toContain('work');
    expect(phases.textContent).toContain('blocked');

    expect(phases.textContent).not.toContain('74');
    expect(phases.textContent).not.toContain('3d 2h');
  });

  it('omits a phase nothing was spent in rather than drawing it at nothing wide', () => {
    render(
      <TaskNodeCard
        node={{ ...blockedAndOverdue, phases: [{ phase: 'active', seconds: 2 * HOUR }] }}
        now={NOW}
      />,
    );

    const segments = screen.getAllByTestId('phase-segment');
    expect(segments).toHaveLength(1);
    expect(screen.getByTestId('canvas-node-phases').textContent).not.toContain('blocked');
  });

  it('keeps its width whatever the title, because the columns are what the plane is for', () => {
    const shortTitle = render(<TaskNodeCard node={pendingStep} />);
    const short = shortTitle.getByRole('button').style.width;
    cleanup();

    render(
      <TaskNodeCard
        node={{
          ...pendingStep,
          title: 'Reconcile the supplier ledger against the quarterly statement and file it',
        }}
      />,
    );

    expect(screen.getByRole('button').style.width).toBe(short);
    expect(short).toBe('228px');
  });

  it('offers a call to action only where the viewer’s next move actually is', () => {
    render(<TaskNodeCard node={pendingStep} />);
    expect(screen.queryByRole('button', { name: en.canvas.node.assign })).toBeNull();
    cleanup();

    render(
      <TaskNodeCard
        node={{
          id: 'step-5',
          title: 'Payroll enrolment',
          condition: 'Reachable',
          stepNumber: 5,
          offersAssignment: true,
        }}
        onAssign={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: en.canvas.node.assign })).toBeTruthy();
  });

  it('draws a step nobody can start yet as dashed, on the plane rather than on a card', () => {
    render(<TaskNodeCard node={pendingStep} />);

    const node = screen.getByRole('button');

    expect(node.style.borderStyle).toBe('dashed');
    expect(node.style.background).toBe('var(--bg)');

    expect(node.className).toContain('canvas-node-pending');
  });

  it('drops the at-risk chip once the deadline has actually passed', () => {
    render(
      <TaskNodeCard
        node={{ ...blockedAndOverdue, atRisk: true, blockedReason: undefined }}
        now={NOW}
      />,
    );

    expect(screen.getByText(en.canvas.node.overdue)).toBeTruthy();
    expect(screen.queryByText(en.canvas.node.atRisk)).toBeNull();
  });

  it('shows at risk while the deadline is still ahead', () => {
    render(
      <TaskNodeCard
        node={{
          id: 'a',
          title: 'Collect the annexes',
          condition: 'Assigned',
          taskState: 'InProgress',
          dueAt: '2026-08-15T09:00:00Z',
          atRisk: true,
        }}
        now={NOW}
      />,
    );

    expect(screen.getByText(en.canvas.node.atRisk)).toBeTruthy();
    expect(screen.queryByText(en.canvas.node.overdue)).toBeNull();
  });

  it('is reachable and selectable by keyboard', async () => {
    const selected = vi.fn();
    render(<TaskNodeCard node={pendingStep} onSelect={selected} />);

    const node = screen.getByRole('button', { name: /First-day briefing/ });
    node.focus();
    await userEvent.keyboard('{Enter}');

    expect(selected).toHaveBeenCalledWith('step-6');
  });

  it('does not steal the keys of the one control inside it', async () => {
    const selected = vi.fn();
    render(
      <TaskNodeCard
        node={{
          id: 'step-5',
          title: 'Payroll enrolment',
          condition: 'Reachable',
          stepNumber: 5,
          offersAssignment: true,
        }}
        onSelect={selected}
        onAssign={vi.fn()}
      />,
    );

    screen.getByRole('button', { name: en.canvas.node.assign }).focus();
    await userEvent.keyboard('{Enter}');

    expect(selected).not.toHaveBeenCalled();
  });
});

describe('a step nobody can start yet', () => {
  it('carries no clock and nobody’s name, whatever it is handed', () => {
    render(
      <TaskNodeCard
        node={{
          id: 'step-9',
          title: 'First-day briefing',
          condition: 'Pending',
          stepNumber: 9,
          assignee: { name: 'Dan Stan' },
          dueAt: '2026-08-10T09:00:00Z',
          phases: [{ phase: 'active', seconds: HOUR }],
        }}
        now={NOW}
      />,
    );

    expect(screen.queryByText('Dan Stan')).toBeNull();
    expect(screen.queryByTestId('canvas-node-due')).toBeNull();
    expect(screen.queryByTestId('canvas-node-phases')).toBeNull();
    expect(screen.queryByText(en.canvas.node.overdue)).toBeNull();
  });
});

describe('work a reviewer has sent back', () => {
  it('shows the note in the same strip a blocker uses, worded as iteration', () => {
    render(
      <TaskNodeCard
        node={{
          id: 'step-7',
          title: 'Draft the induction plan',
          condition: 'Assigned',
          taskState: 'InProgress',
          reworkNote: 'Please add the safety briefing',
        }}
        now={NOW}
      />,
    );

    const strip = screen.getByTestId('canvas-node-note');

    expect(strip.textContent).toContain('Please add the safety briefing');
    expect(screen.queryByTestId('canvas-node-blocker')).toBeNull();

    expect(screen.getByRole('button').textContent).not.toMatch(/reject/i);
  });
});

describe('a node that is both blocked and late', () => {
  it('renders both, with neither masking the other', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    expect(screen.getByText(en.ui.taskState.Blocked)).toBeTruthy();
    expect(screen.getByText(en.canvas.node.overdue)).toBeTruthy();
    expect(screen.getByTestId('canvas-node-blocker')).toBeTruthy();
  });

  it('takes the overdue border, which outranks the block', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    expect(screen.getByRole('button').style.borderColor).toBe('var(--alert-line)');
  });

  it('shows the passed deadline in the alert tone, on the value and never on the label', () => {
    render(<TaskNodeCard node={blockedAndOverdue} now={NOW} />);

    const due = screen.getByTestId('canvas-node-due');
    expect(due.style.color).toBe('var(--alert)');
    expect(screen.getByText(en.canvas.node.due).style.color).not.toBe('var(--alert)');
  });
});

describe('the plane’s one call to action', () => {
  it('carries the press to whoever can act on it, without also selecting the node', () => {
    const assign = vi.fn();
    const select = vi.fn();

    render(
      <TaskNodeCard
        node={{ ...pendingStep, condition: 'Reachable', offersAssignment: true }}
        onAssign={assign}
        onSelect={select}
        now={NOW}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: en.canvas.node.assign }));

    expect(assign).toHaveBeenCalledTimes(1);

    expect(select).not.toHaveBeenCalled();
  });

  it('offers nothing to press where nobody supplied a way to act', () => {
    render(
      <TaskNodeCard
        node={{ ...pendingStep, condition: 'Reachable', offersAssignment: true }}
        now={NOW}
      />,
    );

    expect(screen.queryByRole('button', { name: en.canvas.node.assign })).toBeNull();
  });
});
