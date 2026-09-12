// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { TaskDetail } from '../api/taskApi';
import { TaskDrawer } from './TaskDrawer';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),

  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let detail: { data: TaskDetail | undefined; isError: boolean } = {
  data: undefined,
  isError: false,
};

const idle = {
  mutate: vi.fn(),
  isPending: false,
  isError: false,
  error: null,
  variables: undefined,
};

const fileMutate = vi.fn();
let categoriesState: {
  data?: {
    categories: Array<{ id: string; name: string; taskCount: number }>;
    filings: Record<string, string>;
  };
} = { data: { categories: [], filings: {} } };

vi.mock('../hooks/useTaskCategories', () => ({
  useTaskCategories: () => categoriesState,
  useFileTaskUnder: () => ({ isPending: false, mutate: fileMutate }),
}));

vi.mock('../hooks/useTasks', () => ({
  useTaskDetail: () => detail,
  useAcceptTask: () => idle,
  useStartTask: () => idle,
  useUnblockTask: () => idle,
  useCloseTask: () => idle,
  useTaskMaterial: () => ({ data: undefined, isPending: false, isError: false }),
  useAttachLink: () => idle,
  useDetachLink: () => idle,
  useAddChecklistItem: () => idle,
  useTickChecklistItem: () => idle,
  useRemoveChecklistItem: () => idle,
  useSetDeadline: () => ({ ...idle, reset: vi.fn() }),
  useProposeDeadline: () => ({ ...idle, reset: vi.fn() }),
  useDecideDeadline: () => ({ ...idle, reset: vi.fn() }),
  useEditTask: () => ({ ...idle, reset: vi.fn() }),
  useRejectTask: () => ({ ...idle, reset: vi.fn() }),
  useBlockTask: () => ({ ...idle, reset: vi.fn() }),
  useCompleteTask: () => ({ ...idle, reset: vi.fn() }),
  useApproveTask: () => ({ ...idle, reset: vi.fn() }),
  useRequestChanges: () => ({ ...idle, reset: vi.fn() }),

  useTaskActivity: () => ({ data: { entries: [] }, isError: false, isPending: false }),
  useCommentOnTask: () => ({ ...idle, reset: vi.fn() }),
}));

afterEach(() => {
  cleanup();
  detail = { data: undefined, isError: false };
  categoriesState = { data: { categories: [], filings: {} } };
  document.querySelectorAll('dialog').forEach((element) => element.remove());
});

function task(over: Partial<TaskDetail> = {}): TaskDetail {
  return {
    id: 't1',
    templateId: null,
    title: 'Sună furnizorul',
    description: 'Trebuie să sunăm furnizorul până joi',
    assigneeId: 'p-john',
    assigneeName: 'John Marinescu',
    creatorId: 'p-pascal',
    deadline: null,
    priority: 'NORMAL',
    state: 'CREATED',
    selfAssigned: false,
    atRisk: false,
    createdAt: '2026-08-18T09:00:00Z',
    openPhase: null,
    phaseSince: null,
    phases: [],
    proof: null,
    approval: null,
    completedAt: null,
    deadlineMet: null,
    deadlineProposal: null,
    ...over,
  };
}

function open(onClose: () => void = () => {}): void {
  render(
    <TaskDrawer
      taskId="t1"
      viewerId="p-pascal"
      permissions={['TASK_VIEW_OWN']}
      onClose={onClose}
    />,
  );
}

describe('the task drawer', () => {
  it('says it is reading rather than showing an empty panel', () => {
    open();

    expect(screen.getByRole('dialog', { name: 'task.drawer.loading' })).toBeDefined();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('says so when the read fails, in a region that announces itself', () => {
    detail = { data: undefined, isError: true };

    open();

    expect(screen.getByRole('alert').textContent).toBe('task.loadFailed');
  });

  it('carries the task in full, and the message it was converted from as its description', () => {
    detail = { data: task(), isError: false };

    open();

    expect(screen.getByRole('heading', { name: 'Sună furnizorul' })).toBeDefined();
    expect(screen.getByText('John Marinescu')).toBeDefined();
    expect(screen.getByText('Trebuie să sunăm furnizorul până joi')).toBeDefined();
    expect(screen.getByText('task.noDeadline')).toBeDefined();
  });

  it('names a former member as one rather than leaving the row blank', () => {
    detail = { data: task({ assigneeName: '' }), isError: false };

    open();

    expect(screen.getByText('task.formerMember')).toBeDefined();
  });

  it('reads a phase in hours and minutes, never as a bare number', () => {
    detail = {
      data: task({
        phases: [
          { kind: 'WAIT', seconds: 5400 },
          { kind: 'WORK', seconds: 2700 },
        ] as TaskDetail['phases'],
      }),
      isError: false,
    };

    open();

    expect(screen.getByText('1h 30m')).toBeDefined();
    expect(screen.getByText('45m')).toBeDefined();
  });

  it('closes on Escape, from anywhere on the page', () => {
    const onClose = vi.fn();
    detail = { data: task(), isError: false };

    open(onClose);
    fireEvent.keyDown(window, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('leaves the drawer open when Escape was meant for a dialog inside it', () => {
    const onClose = vi.fn();
    detail = { data: task(), isError: false };

    open(onClose);
    const inner = document.createElement('dialog');
    inner.setAttribute('open', '');
    document.body.appendChild(inner);

    fireEvent.keyDown(window, { key: 'Escape' });

    expect(onClose).not.toHaveBeenCalled();
  });

  it('closes on the close control', () => {
    const onClose = vi.fn();
    detail = { data: task(), isError: false };

    open(onClose);
    fireEvent.click(screen.getByRole('button', { name: 'task.drawer.close' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  describe('the grouping', () => {
    const AURORA = { id: 'cat-1', name: 'Aurora Coffee', taskCount: 2 };

    it('files the task under the grouping that was picked', () => {
      categoriesState = { data: { categories: [AURORA], filings: {} } };
      detail = { data: task(), isError: false };
      fileMutate.mockReset();

      render(
        <TaskDrawer
          taskId="t1"
          viewerId="p-pascal"
          permissions={['TASK_VIEW_OWN', 'TASK_EDIT']}
          onClose={() => {}}
        />,
      );
      fireEvent.change(screen.getByLabelText('task.drawer.category'), {
        target: { value: 'cat-1' },
      });

      expect(fileMutate).toHaveBeenCalledWith({ taskId: 't1', categoryId: 'cat-1' });
    });

    it('takes the task out of its grouping when the blank option is chosen', () => {
      categoriesState = { data: { categories: [AURORA], filings: { t1: 'cat-1' } } };
      detail = { data: task(), isError: false };
      fileMutate.mockReset();

      render(
        <TaskDrawer
          taskId="t1"
          viewerId="p-pascal"
          permissions={['TASK_VIEW_OWN', 'TASK_EDIT']}
          onClose={() => {}}
        />,
      );
      fireEvent.change(screen.getByLabelText('task.drawer.category'), { target: { value: '' } });

      expect(fileMutate).toHaveBeenCalledWith({ taskId: 't1', categoryId: null });
    });

    it('shows the grouping as text to somebody who may not edit the task', () => {
      categoriesState = { data: { categories: [AURORA], filings: { t1: 'cat-1' } } };
      detail = { data: task(), isError: false };

      open();

      expect(screen.queryByLabelText('task.drawer.category')).toBeNull();
      expect(screen.getByText('Aurora Coffee')).toBeTruthy();
    });
  });
});
