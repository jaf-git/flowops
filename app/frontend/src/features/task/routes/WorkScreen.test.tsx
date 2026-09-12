// @vitest-environment jsdom

import { cleanup, fireEvent, render as renderBare, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

import type { TaskSummary } from '../api/taskApi';
import { WorkScreen } from './WorkScreen';
import { NoticeCentre } from '../../../shared/notice/NoticeCentre';
import { NoticeProvider } from '../../../shared/notice/NoticeProvider';

function render(ui: Parameters<typeof renderBare>[0]): ReturnType<typeof renderBare> {
  return renderBare(
    <NoticeProvider>
      <MemoryRouter>{ui}</MemoryRouter>
      <NoticeCentre label="notices" dismissLabel="dismiss" />
    </NoticeProvider>,
  );
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: unknown) =>
      typeof options === 'object' && options !== null && 'name' in options
        ? `${key}:${(options as { name: string }).name}`
        : key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

vi.mock('../components/CreateTaskDialog', () => ({
  CreateTaskDialog: ({
    open,
    onCreated,
  }: {
    open: boolean;
    onCreated: (task: { assigneeName: string }) => void;
  }) =>
    open ? (
      <button type="button" onClick={() => onCreated({ assigneeName: 'Andrei Munteanu' })}>
        pretend-to-create
      </button>
    ) : null,
}));

vi.mock('../components/BlockTaskDialog', () => ({
  BlockTaskDialog: ({ taskId, onBlocked }: { taskId: string; onBlocked: () => void }) => (
    <button type="button" onClick={onBlocked}>
      {`pretend-to-block:${taskId}`}
    </button>
  ),
}));

vi.mock('../components/CompleteTaskDialog', () => ({
  CompleteTaskDialog: ({ taskId, onCompleted }: { taskId: string; onCompleted: () => void }) => (
    <button type="button" onClick={onCompleted}>
      {`pretend-to-complete:${taskId}`}
    </button>
  ),
}));

vi.mock('../components/ReviewQueue', () => ({
  ReviewQueue: ({
    permissions,
    onOpen,
  }: {
    permissions: readonly string[];
    onOpen: (id: string) => void;
  }) =>
    permissions.includes('TASK_REVIEW') ? (
      <button type="button" onClick={() => onOpen('task-1')}>
        pretend-review-queue
      </button>
    ) : null,
}));

vi.mock('../components/TaskReviewPanel', () => ({
  TaskReviewPanel: ({
    taskId,
    onApproved,
    onReturned,
  }: {
    taskId: string;
    onApproved: () => void;
    onReturned: () => void;
  }) => (
    <>
      <button type="button" onClick={onApproved}>
        {`pretend-to-review:${taskId}`}
      </button>

      <button type="button" onClick={onReturned}>
        {`pretend-to-send-back:${taskId}`}
      </button>
    </>
  ),
}));

vi.mock('../components/RejectTaskDialog', () => ({
  RejectTaskDialog: ({ taskId, onRejected }: { taskId: string; onRejected: () => void }) => (
    <button type="button" onClick={onRejected}>
      {`pretend-to-decline:${taskId}`}
    </button>
  ),
}));

vi.mock('../components/ProposeDeadlineDialog', () => ({
  ProposeDeadlineDialog: ({ taskId, onProposed }: { taskId: string; onProposed: () => void }) => (
    <button type="button" onClick={onProposed}>
      {`pretend-to-propose:${taskId}`}
    </button>
  ),
}));

vi.mock('../components/DecideDeadlineDialog', () => ({
  DecideDeadlineDialog: ({ taskId, onDecided }: { taskId: string; onDecided: () => void }) => (
    <button type="button" onClick={onDecided}>
      {`pretend-to-decide:${taskId}`}
    </button>
  ),
}));

vi.mock('../components/EditTaskDialog', () => ({
  EditTaskDialog: ({ task: subject, onEdited }: { task: { id: string }; onEdited: () => void }) => (
    <button type="button" onClick={onEdited}>
      {`pretend-to-edit:${subject.id}`}
    </button>
  ),
}));

const acceptMutate = vi.fn();
const startMutate = vi.fn();
const unblockMutate = vi.fn();
const closeMutate = vi.fn();
let tasksState: {
  isPending: boolean;
  isError: boolean;
  data: { tasks: TaskSummary[] } | undefined;
} = {
  isPending: false,
  isError: false,
  data: { tasks: [] },
};
let acceptState = { isError: false, isPending: false, variables: undefined as string | undefined };
let startState = { isError: false, isPending: false, variables: undefined as string | undefined };
let unblockState = {
  isError: false,
  isPending: false,
  variables: undefined as { id: string } | undefined,
};
let closeState = { isError: false, isPending: false, variables: undefined as string | undefined };
let detailState: { isPending: boolean; isError: boolean; data: unknown } = {
  isPending: false,
  isError: false,
  data: {
    id: 'task-1',
    priority: 'NORMAL',
    deadline: '2026-09-01T09:00:00Z',
    description: null,
    deadlineProposal: {
      proposedDeadline: '2026-09-08T09:00:00Z',
      reason: 'The parts arrive Friday',
      proposerId: 'andrei',
      proposedAt: '2026-08-11T09:00:00Z',
    },
  },
};

const acknowledgeMutate = vi.fn();
const noticesState: { data?: { notices: unknown[] } } = { data: { notices: [] } };

let categoriesState: {
  data?: {
    categories: Array<{ id: string; name: string; taskCount: number }>;
    filings: Record<string, string>;
  };
} = { data: { categories: [], filings: {} } };

const sectionsFilters: unknown[] = [];

vi.mock('../hooks/useTaskCategories', () => ({
  useTaskCategories: () => categoriesState,
  useCreateTaskCategory: () => ({ isPending: false, isError: false, mutate: vi.fn() }),
  useDeleteTaskCategory: () => ({ isPending: false, mutate: vi.fn() }),
  useFileTaskUnder: () => ({ isPending: false, mutate: vi.fn() }),
}));

vi.mock('../hooks/useTasks', () => ({
  useTasks: () => tasksState,

  useTaskSections: (filter: unknown) => (
    sectionsFilters.push(filter),
    {
      ...tasksState,
      data:
        tasksState.data === undefined
          ? undefined
          : {
              sections: [{ section: 'needs-you', count: tasksState.data.tasks.length }],
              total: tasksState.data.tasks.length,
            },
    }
  ),
  useTaskSection: () => ({
    ...tasksState,
    data:
      tasksState.data === undefined
        ? undefined
        : {
            section: 'needs-you',
            rows: tasksState.data.tasks,
            page: 0,
            size: 10,
            total: tasksState.data.tasks.length,
            totalPages: 1,
          },
  }),
  useAcceptTask: () => ({ ...acceptState, mutate: acceptMutate }),
  useStartTask: () => ({ ...startState, mutate: startMutate }),
  useUnblockTask: () => ({ ...unblockState, mutate: unblockMutate }),
  useCloseTask: () => ({ ...closeState, mutate: closeMutate }),
  useTaskDetail: () => detailState,

  useDeadlineNotices: () => noticesState,
  useAcknowledgeDeadlineNotice: () => ({ mutate: acknowledgeMutate, isPending: false }),
  useSetDeadline: () => ({ mutate: vi.fn(), isPending: false, reset: vi.fn(), error: null }),
}));

function task(overrides: Partial<TaskSummary> = {}): TaskSummary {
  return {
    id: 'task-1',
    title: 'Draft the supplier review',
    assigneeId: 'andrei',
    assigneeName: 'Andrei Munteanu',
    deadline: '2026-09-01T09:00:00Z',
    priority: 'NORMAL',
    state: 'CREATED',
    openPhase: 'WAIT',
    phaseSince: '2026-08-10T09:00:00Z',
    mine: true,
    directedByMe: false,
    deadlineProposalOpen: false,
    atRisk: false,
    kind: 'TASK',
    templateId: null,
    categoryId: null,
    categoryName: null,
    ...overrides,
  };
}

beforeEach(() => {
  categoriesState = { data: { categories: [], filings: {} } };
  sectionsFilters.length = 0;
  tasksState = { isPending: false, isError: false, data: { tasks: [] } };
  acceptState = { isError: false, isPending: false, variables: undefined };
  startState = { isError: false, isPending: false, variables: undefined };
  unblockState = { isError: false, isPending: false, variables: undefined };
  closeState = { isError: false, isPending: false, variables: undefined };
  detailState = {
    isPending: false,
    isError: false,
    data: {
      id: 'task-1',
      priority: 'NORMAL',
      deadline: '2026-09-01T09:00:00Z',
      description: null,
      deadlineProposal: {
        proposedDeadline: '2026-09-08T09:00:00Z',
        reason: 'The parts arrive Friday',
        proposerId: 'andrei',
        proposedAt: '2026-08-11T09:00:00Z',
      },
    },
  };
  closeMutate.mockReset();
  acceptMutate.mockReset();
  startMutate.mockReset();
  unblockMutate.mockReset();
});

afterEach(cleanup);

describe('WorkScreen', () => {
  it('tells somebody with nothing waiting that nothing is waiting, rather than showing a blank list', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.empty.heading')).toBeTruthy();
  });

  it('offers the create control to somebody who holds TASK_CREATE', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_CREATE']} />);

    expect(screen.queryByText('task.create.open')).not.toBeNull();
  });

  it('does not offer it to somebody who does not, and does not merely disable it', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.queryByText('task.create.open')).toBeNull();
  });

  it('names the phase beside the instant its clock belongs to, never a bare duration', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.phase.WAIT')).toBeTruthy();
  });

  it('opens the decline dialog when the assignee says the work is not theirs', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_ACT_OWN']} />);
    fireEvent.click(screen.getByText('task.reject.action'));

    expect(screen.getByText('pretend-to-decline:task-1')).toBeTruthy();
  });

  it('opens the date dialog when the assignee asks for a different one', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'IN_PROGRESS', openPhase: 'ACTIVE' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_ACT_OWN']} />);
    fireEvent.click(screen.getByText('task.proposeDeadline.action'));

    expect(screen.getByText('pretend-to-propose:task-1')).toBeTruthy();
  });

  it('opens the set-date dialog from Accepted', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'ACCEPTED' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_ACT_OWN']} />);

    expect(screen.getByText('task.setDeadline.action')).toBeTruthy();
  });

  it('opens the decision dialog for the person who assigned the work', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ mine: false, directedByMe: true, deadlineProposalOpen: true })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_DECIDE_DEADLINE']} />);
    fireEvent.click(screen.getByText('task.decideDeadline.action'));

    expect(screen.getByText('pretend-to-decide:task-1')).toBeTruthy();
  });

  it('does not offer editing from the queue — the task has to be opened', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ mine: false, directedByMe: true })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_EDIT']} />);

    expect(screen.queryByText('task.edit.action')).toBeNull();

    expect(screen.getByText('Draft the supplier review')).toBeTruthy();
  });

  it('says the read failed rather than claiming nothing is waiting, when the read fails', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ mine: false, directedByMe: true, deadlineProposalOpen: true })] },
    };
    detailState = { isPending: false, isError: true, data: undefined };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_DECIDE_DEADLINE']} />);
    fireEvent.click(screen.getByText('task.decideDeadline.action'));

    expect(screen.getByText('task.loadFailed')).toBeTruthy();
    expect(screen.queryByText('task.decideDeadline.error.NO_OPEN_PROPOSAL')).toBeNull();
  });

  it('says nothing is waiting when the read worked and found no proposal', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ mine: false, directedByMe: true, deadlineProposalOpen: true })] },
    };
    detailState = {
      isPending: false,
      isError: false,
      data: { id: 'task-1', deadlineProposal: null },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_DECIDE_DEADLINE']} />);
    fireEvent.click(screen.getByText('task.decideDeadline.action'));

    expect(screen.getByText('task.decideDeadline.error.NO_OPEN_PROPOSAL')).toBeTruthy();
    expect(screen.queryByText('task.loadFailed')).toBeNull();
  });

  it('shows work the workspace called at risk as at risk', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ deadline: '2099-01-01T09:00:00Z', atRisk: true })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText(/ui\.deadline\.at-risk/)).toBeTruthy();
  });

  it('and shows work outside the window as on time', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ deadline: '2099-01-01T09:00:00Z', atRisk: false })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText(/ui\.deadline\.on-time/)).toBeTruthy();
    expect(screen.queryByText(/ui\.deadline\.at-risk/)).toBeNull();
  });

  it('offers Accept on my own work while it is waiting to be accepted', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getByText('task.accept.action'));

    expect(acceptMutate).toHaveBeenCalledWith('task-1');
  });

  it('does not offer it on somebody else’s work, even in the same state', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task({ mine: false })] } };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.queryByText('task.accept.action')).toBeNull();
  });

  it('does not offer it on my own work that has already been accepted', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'ACCEPTED' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.queryByText('task.accept.action')).toBeNull();
  });

  it('says former member where the name has been erased, rather than leaving a gap', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ assigneeName: '' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.formerMember')).toBeTruthy();
  });

  it('reports a failed acceptance on the screen rather than in something that disappears', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };
    acceptState = { isError: true, isPending: false, variables: undefined };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.moved.failed')).toBeTruthy();
  });

  it('disables the button it is submitting, so a slow connection cannot produce two acceptances', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };
    acceptState = { isError: false, isPending: true, variables: 'task-1' };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.accept.submitting')).toBeTruthy();
    expect(screen.queryByText('task.accept.action')).toBeNull();
  });

  it('leaves every other row usable while one is in flight', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task(), task({ id: 'task-2', title: 'Chase the timber invoice' })] },
    };
    acceptState = { isError: false, isPending: true, variables: 'task-1' };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getAllByText('task.accept.submitting')).toHaveLength(1);
    expect(screen.getAllByText('task.accept.action')).toHaveLength(1);
  });

  it('says the task is finished rather than timing a phase that is not open', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'CLOSED', openPhase: null, phaseSince: null })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.phase.none')).toBeTruthy();
  });

  it('shows a spinner while the queue is loading and no empty state behind it', () => {
    tasksState = { isPending: true, isError: false, data: undefined };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.loading')).toBeTruthy();
    expect(screen.queryByText('task.empty.heading')).toBeNull();
  });

  it('names the person who was given the work once the server has agreed', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_CREATE']} />);
    fireEvent.click(screen.getByText('task.create.open'));
    fireEvent.click(screen.getByText('pretend-to-create'));

    expect(screen.getByText('task.create.given:Andrei Munteanu')).toBeTruthy();
  });

  it('starts the work and says that the clock has begun', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'ACCEPTED', openPhase: 'WAIT' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getByText('task.start.action'));

    expect(startMutate).toHaveBeenCalledTimes(1);
    expect(startMutate.mock.calls[0]?.[0]).toBe('task-1');
  });

  it('says nothing about a clock when the work is merely acknowledged', () => {
    tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getByText('task.accept.action'));

    expect(acceptMutate).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('task.moved.started')).toBeNull();
  });

  it('lets somebody carry on without asking them to explain the good news', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'BLOCKED', openPhase: 'BLOCKED' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getByText('task.unblock.action'));

    expect(unblockMutate).toHaveBeenCalledTimes(1);
    expect(unblockMutate.mock.calls[0]?.[0]).toEqual({ id: 'task-1', resolution: '' });
  });

  it('opens the block dialog for the task whose button was pressed', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: {
        tasks: [
          task({ id: 'task-1', state: 'IN_PROGRESS', openPhase: 'ACTIVE' }),
          task({
            id: 'task-2',
            state: 'IN_PROGRESS',
            openPhase: 'ACTIVE',
            title: 'Chase the invoice',
          }),
        ],
      },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getAllByText('task.block.action')[1] as HTMLElement);

    expect(screen.getByText('pretend-to-block:task-2')).toBeTruthy();
    expect(screen.queryByText('pretend-to-block:task-1')).toBeNull();
  });

  it('announces that the clock has stopped once the block has been recorded', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'IN_PROGRESS', openPhase: 'ACTIVE' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getByText('task.block.action'));
    fireEvent.click(screen.getByText('pretend-to-block:task-1'));

    expect(screen.getByText('task.moved.blocked')).toBeTruthy();
  });

  it('opens the completion dialog and announces that the work has gone for review', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'IN_PROGRESS', openPhase: 'ACTIVE' })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
    fireEvent.click(screen.getByText('task.complete.action'));
    fireEvent.click(screen.getByText('pretend-to-complete:task-1'));

    expect(screen.getByText('task.moved.completed')).toBeTruthy();
  });

  it('disables the rail of the row whose start is in flight, and no other row', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: {
        tasks: [
          task({ id: 'task-1', state: 'ACCEPTED' }),
          task({ id: 'task-2', state: 'ACCEPTED', title: 'Chase the invoice' }),
        ],
      },
    };
    startState = { isError: false, isPending: true, variables: 'task-1' };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getAllByText('task.start.submitting')).toHaveLength(1);
    expect(screen.getAllByText('task.start.action')).toHaveLength(1);
  });

  it('reports a failed unblock on the screen, not only a failed acceptance', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'BLOCKED', openPhase: 'BLOCKED' })] },
    };
    unblockState = { isError: true, isPending: false, variables: undefined };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.moved.failed')).toBeTruthy();
  });

  it('disables the rail of the row whose unblock is in flight', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: {
        tasks: [
          task({ id: 'task-1', state: 'BLOCKED', openPhase: 'BLOCKED' }),
          task({
            id: 'task-2',
            state: 'BLOCKED',
            openPhase: 'BLOCKED',
            title: 'Chase the invoice',
          }),
        ],
      },
    };
    unblockState = { isError: false, isPending: true, variables: { id: 'task-1' } };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getAllByText('task.unblock.submitting')).toHaveLength(1);
    expect(screen.getAllByText('task.unblock.action')).toHaveLength(1);
  });

  it('reports a failed start, which is the clause between the two that were tested', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ state: 'ACCEPTED' })] },
    };
    startState = { isError: true, isPending: false, variables: undefined };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.moved.failed')).toBeTruthy();
  });

  it('shows the review queue to somebody who holds TASK_REVIEW', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_REVIEW']} />);

    expect(screen.getByText('pretend-review-queue')).toBeTruthy();
  });

  it('shows no review queue to somebody who does not', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.queryByText('pretend-review-queue')).toBeNull();
  });

  it('opens the review panel on the task the queue names', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_REVIEW']} />);

    fireEvent.click(screen.getByText('pretend-review-queue'));

    expect(screen.getByText('pretend-to-review:task-1')).toBeTruthy();
  });

  it('announces an approval once the panel reports one', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_REVIEW']} />);
    fireEvent.click(screen.getByText('pretend-review-queue'));

    fireEvent.click(screen.getByText('pretend-to-review:task-1'));

    expect(screen.getByText('task.moved.approved')).toBeTruthy();
  });

  it('does not offer closing from the queue, whatever the viewer holds', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: {
        tasks: [
          task({
            state: 'APPROVED',
            mine: false,
            directedByMe: false,
            deadlineProposalOpen: false,
            atRisk: false,
            openPhase: 'APPROVAL',
          }),
        ],
      },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_CLOSE']} />);

    expect(screen.queryByText('task.close.action')).toBeNull();
    expect(closeMutate).not.toHaveBeenCalled();
  });

  it('announces a return once the panel reports one', () => {
    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_REVIEW']} />);
    fireEvent.click(screen.getByText('pretend-review-queue'));

    fireEvent.click(screen.getByText('pretend-to-send-back:task-1'));

    expect(screen.getByText('task.moved.returned')).toBeTruthy();
  });

  it('says so when a close could not be recorded', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: {
        tasks: [
          task({
            state: 'APPROVED',
            mine: false,
            directedByMe: false,
            deadlineProposalOpen: false,
            atRisk: false,
            openPhase: 'APPROVAL',
          }),
        ],
      },
    };
    closeState = { isError: true, isPending: false, variables: undefined };

    render(<WorkScreen permissions={['TASK_VIEW_OWN', 'TASK_CLOSE']} />);

    expect(screen.getByText('task.moved.failed')).toBeTruthy();
  });

  it('offers the links and steps control on nobody else’s work', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ mine: false, directedByMe: false })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.queryByText('task.material.open')).toBeNull();
  });

  it('offers it on work somebody gave out, because they may read what it is done from', () => {
    tasksState = {
      isPending: false,
      isError: false,
      data: { tasks: [task({ mine: false, directedByMe: true })] },
    };

    render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('task.material.open')).toBeTruthy();
  });

  describe('opening a task in full', () => {
    it('opens the one whose title was pressed', () => {
      const onOpenTask = vi.fn();
      tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };

      render(<WorkScreen permissions={['TASK_VIEW_OWN']} onOpenTask={onOpenTask} />);
      fireEvent.click(screen.getByRole('button', { name: 'Draft the supplier review' }));

      expect(onOpenTask).toHaveBeenCalledWith('task-1');
    });

    it('leaves the title as text where there is nothing to open it into', () => {
      tasksState = { isPending: false, isError: false, data: { tasks: [task()] } };

      render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

      expect(screen.queryByRole('button', { name: 'Draft the supplier review' })).toBeNull();
      expect(screen.getByText('Draft the supplier review')).toBeTruthy();
    });
  });

  describe('narrowing to a grouping', () => {
    const AURORA = { id: 'cat-1', name: 'Aurora Coffee', taskCount: 2 };

    it('offers no grouping filter until a grouping exists', () => {
      render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

      expect(screen.queryByLabelText('task.filter.category')).toBeNull();
    });

    it('asks the server for the grouping that was picked', () => {
      categoriesState = { data: { categories: [AURORA], filings: {} } };

      render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);
      fireEvent.change(screen.getByLabelText('task.filter.category'), {
        target: { value: 'cat-1' },
      });

      expect(sectionsFilters.at(-1)).toMatchObject({ category: 'cat-1' });
    });

    it('names the grouping a row is filed under', () => {
      categoriesState = { data: { categories: [AURORA], filings: {} } };
      tasksState = {
        isPending: false,
        isError: false,
        data: { tasks: [task({ categoryId: 'cat-1', categoryName: 'Aurora Coffee' })] },
      };

      render(<WorkScreen permissions={['TASK_VIEW_OWN']} />);

      expect(screen.getByTitle('task.category.filedUnder').textContent).toBe('Aurora Coffee');
    });
  });
});
