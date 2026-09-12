// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { EditTaskDialog } from './EditTaskDialog';
import type { Task } from '../api/taskApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
const reset = vi.fn();
let state: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useTasks', () => ({
  useEditTask: () => ({ ...state, mutate, reset }),
}));

beforeEach(() => {
  mutate.mockReset();
  reset.mockReset();
  state = { isPending: false, error: undefined };
});

afterEach(cleanup);

const TASK: Task = {
  id: 'a-task',
  title: 'Rebuild the supplier list',
  description: 'Before the audit',
  assigneeId: 'andrei',
  assigneeName: 'Andrei Pop',
  creatorId: 'ionut',
  deadline: '2026-09-20T15:00:00Z',
  priority: 'NORMAL',
  state: 'CREATED',
  selfAssigned: false,
  atRisk: false,
  createdAt: '2026-08-11T09:00:00Z',
};

function open() {
  return render(<EditTaskDialog task={TASK} onClose={vi.fn()} onEdited={vi.fn()} />);
}

describe('editing a task', () => {
  it('will not save while nothing differs from what the task already says', () => {
    open();

    expect(
      (screen.getByRole('button', { name: 'task.edit.submit' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(screen.getByText('task.edit.preview.none')).toBeTruthy();
  });

  it('saves once something has actually moved', () => {
    open();

    fireEvent.change(screen.getByLabelText('task.edit.field.priority'), {
      target: { value: 'URGENT' },
    });

    expect(
      (screen.getByRole('button', { name: 'task.edit.submit' }) as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('stops saying nothing differs once something does', () => {
    open();

    fireEvent.change(screen.getByLabelText('task.edit.field.priority'), {
      target: { value: 'URGENT' },
    });

    expect(screen.queryByText('task.edit.preview.none')).toBeNull();
  });

  it('warns that moving the deadline tells the assignee', () => {
    open();

    fireEvent.change(screen.getByLabelText('task.edit.field.deadline'), {
      target: { value: '2026-10-01T15:00' },
    });

    expect(screen.getByText('task.edit.notice.deadline')).toBeTruthy();
  });

  it('does not warn about the deadline when only the priority moved', () => {
    open();

    fireEvent.change(screen.getByLabelText('task.edit.field.priority'), {
      target: { value: 'URGENT' },
    });

    expect(screen.queryByText('task.edit.notice.deadline')).toBeNull();
  });

  it('says plainly that the title and the assignee cannot be changed here', () => {
    open();

    expect(screen.getByText('task.edit.notice.locked')).toBeTruthy();
  });

  it('tells the person when the task has been closed underneath them', () => {
    state = {
      isPending: false,
      error: new ApiError(409, { code: 'TASK_IS_CLOSED', message: 'refused' }),
    };
    open();

    expect(screen.getByText('task.edit.error.TASK_IS_CLOSED')).toBeTruthy();
  });

  it('disables itself while the request is in flight', () => {
    state = { isPending: true, error: undefined };
    open();

    expect(
      (screen.getByRole('button', { name: 'task.edit.submitting' }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});
