// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import en from '../../../i18n/locales/en/common.json';
import { TaskActionsPanel } from './TaskActionsPanel';

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      return typeof phrase === 'string' ? phrase : key;
    },
    i18n: { language: 'en' },
  }),
}));

function standIn(name: string) {
  return ({ taskId }: { taskId: string }) => <p>{`${name}:${taskId}`}</p>;
}

vi.mock('./BlockTaskDialog', () => ({ BlockTaskDialog: standIn('block-dialog') }));
vi.mock('./CompleteTaskDialog', () => ({ CompleteTaskDialog: standIn('complete-dialog') }));
vi.mock('./RejectTaskDialog', () => ({ RejectTaskDialog: standIn('reject-dialog') }));
vi.mock('./ProposeDeadlineDialog', () => ({ ProposeDeadlineDialog: standIn('propose-dialog') }));
vi.mock('./SetDeadlineDialog', () => ({ SetDeadlineDialog: standIn('deadline-dialog') }));
vi.mock('./TaskReviewPanel', () => ({ TaskReviewPanel: standIn('review-panel') }));
vi.mock('./TaskDialogsFromDetail', () => ({
  DecideDeadlineDialogFromDetail: standIn('decide-dialog'),
  EditTaskDialogFromDetail: standIn('edit-dialog'),
}));

const acceptMutate = vi.fn();
const startMutate = vi.fn();
const unblockMutate = vi.fn();
const closeMutate = vi.fn();

let acceptState: { isPending: boolean; error: Error | null } = { isPending: false, error: null };
let startState: { isPending: boolean; error: Error | null } = { isPending: false, error: null };

vi.mock('../hooks/useTasks', () => ({
  useAcceptTask: () => ({ ...acceptState, mutate: acceptMutate }),
  useStartTask: () => ({ ...startState, mutate: startMutate }),
  useUnblockTask: () => ({ isPending: false, error: null, mutate: unblockMutate }),
  useCloseTask: () => ({ isPending: false, error: null, mutate: closeMutate }),
}));

function panel(props: Partial<Parameters<typeof TaskActionsPanel>[0]> = {}) {
  return render(
    <TaskActionsPanel
      taskId="task-1"
      state="IN_PROGRESS"
      mine
      directedByMe={false}
      hasDeadline
      permissions={['TASK_ACT_OWN']}
      {...props}
    />,
  );
}

beforeEach(() => {
  acceptState = { isPending: false, error: null };
  startState = { isPending: false, error: null };
  vi.clearAllMocks();
});

afterEach(cleanup);

describe('acting on a task where you see it', () => {
  it('opens TASK’s own dialog rather than asking for the same thing a second way', () => {
    panel();

    expect(screen.queryByText('block-dialog:task-1')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: en.task.block.action }));

    expect(screen.getByText('block-dialog:task-1')).toBeTruthy();
  });

  it('sends a move that needs no dialog straight to TASK’s own endpoint', () => {
    panel({ state: 'CREATED' });

    fireEvent.click(screen.getByRole('button', { name: en.task.accept.action }));

    expect(acceptMutate).toHaveBeenCalledWith('task-1');

    expect(startMutate).not.toHaveBeenCalled();
    expect(unblockMutate).not.toHaveBeenCalled();
    expect(closeMutate).not.toHaveBeenCalled();
  });

  it('shows the refusal in TASK’s words, and does not soften it', () => {
    startState = {
      isPending: false,
      error: new ApiError(409, {
        code: 'ILLEGAL_TRANSITION',
        message: 'This task is already in progress.',
      }),
    };

    panel({ state: 'ACCEPTED' });

    expect(screen.getByText('This task is already in progress.')).toBeTruthy();
    expect(screen.queryByText(en.task.moved.failed)).toBeNull();
  });

  it('falls back to the general line only where no server ever answered', () => {
    startState = { isPending: false, error: new Error('Failed to fetch') };

    panel({ state: 'ACCEPTED' });

    expect(screen.getByText(en.task.moved.failed)).toBeTruthy();
    expect(screen.queryByText('Failed to fetch')).toBeNull();
  });

  it('offers nothing at all where the viewer has no legal move', () => {
    const { container } = panel({ mine: false, permissions: [] });

    expect(container.querySelectorAll('button')).toHaveLength(0);
  });

  it('stops a second press reaching the endpoint while the first is in flight', () => {
    startState = { isPending: true, error: null };

    panel({ state: 'ACCEPTED' });

    for (const control of screen.getAllByRole('button')) {
      expect((control as HTMLButtonElement).disabled).toBe(true);
    }
  });

  const DIALOGS = [
    ['block', en.task.block.action, 'block-dialog:task-1', 'IN_PROGRESS', true],
    ['complete', en.task.complete.action, 'complete-dialog:task-1', 'IN_PROGRESS', true],
    ['reject', en.task.reject.action, 'reject-dialog:task-1', 'CREATED', true],
    ['propose', en.task.proposeDeadline.action, 'propose-dialog:task-1', 'IN_PROGRESS', true],
    ['deadline', en.task.setDeadline.action, 'deadline-dialog:task-1', 'ACCEPTED', true],
    ['review', en.task.review.action, 'review-panel:task-1', 'COMPLETED', false],
    ['edit', en.task.edit.action, 'edit-dialog:task-1', 'IN_PROGRESS', false],
  ] as const;

  it.each(DIALOGS)('opens TASK’s %s dialog and no other', (_name, label, rendered, state, mine) => {
    panel({
      state,
      mine,
      directedByMe: !mine,
      permissions: ['TASK_ACT_OWN', 'TASK_REVIEW', 'TASK_EDIT'],
    });

    fireEvent.click(screen.getByRole('button', { name: label }));

    expect(screen.getByText(rendered)).toBeTruthy();

    expect(screen.getAllByText(/-dialog:|-panel:/)).toHaveLength(1);
  });

  const MOVES = [
    ['accept', en.task.accept.action, 'CREATED', true, () => acceptMutate],
    ['start', en.task.start.action, 'ACCEPTED', true, () => startMutate],
    ['unblock', en.task.unblock.action, 'BLOCKED', true, () => unblockMutate],
    ['close', en.task.close.action, 'APPROVED', false, () => closeMutate],
  ] as const;

  it.each(MOVES)(
    'sends %s to its own endpoint and to no other',
    (_name, label, state, mine, of) => {
      panel({ state, mine, permissions: ['TASK_ACT_OWN', 'TASK_CLOSE'] });

      fireEvent.click(screen.getByRole('button', { name: label }));

      expect(of()).toHaveBeenCalledTimes(1);
      for (const other of [acceptMutate, startMutate, unblockMutate, closeMutate]) {
        if (other !== of()) {
          expect(other).not.toHaveBeenCalled();
        }
      }
    },
  );

  it('offers no comment anywhere, in this pass', () => {
    const { container } = panel();

    expect(container.textContent?.toLowerCase()).not.toContain('comment');
  });
});
