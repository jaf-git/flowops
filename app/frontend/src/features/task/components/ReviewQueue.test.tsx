// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ReviewQueue } from './ReviewQueue';
import type { TaskSummary } from '../api/taskApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let queueState: {
  isPending: boolean;
  isError: boolean;
  data: { tasks: TaskSummary[] } | undefined;
} = { isPending: false, isError: false, data: { tasks: [] } };

let enabledWith: boolean | undefined;

vi.mock('../hooks/useTasks', () => ({
  useReviewQueue: (enabled: boolean) => {
    enabledWith = enabled;
    return queueState;
  },
}));

function waiting(overrides: Partial<TaskSummary> = {}): TaskSummary {
  return {
    id: 'task-1',
    title: 'Draft the supplier review',
    assigneeId: 'andrei',
    assigneeName: 'Andrei Munteanu',
    deadline: '2026-09-01T09:00:00Z',
    priority: 'NORMAL',
    state: 'COMPLETED',
    openPhase: 'REVIEW',
    phaseSince: '2026-08-10T09:00:00Z',
    mine: false,
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
  queueState = { isPending: false, isError: false, data: { tasks: [] } };
  enabledWith = undefined;
});

afterEach(cleanup);

describe('ReviewQueue', () => {
  it('lists the work waiting on a reviewer', () => {
    queueState = { isPending: false, isError: false, data: { tasks: [waiting()] } };

    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(screen.getByText('Draft the supplier review')).toBeTruthy();
    expect(screen.getByText('Andrei Munteanu')).toBeTruthy();
  });

  it('renders nothing at all for somebody without TASK_REVIEW', () => {
    queueState = { isPending: false, isError: false, data: { tasks: [waiting()] } };

    render(<ReviewQueue permissions={['TASK_VIEW_OWN']} onOpen={vi.fn()} />);

    expect(screen.queryByText('Draft the supplier review')).toBeNull();
  });

  it('does not even ask for the queue when the viewer may not review', () => {
    render(<ReviewQueue permissions={['TASK_VIEW_OWN']} onOpen={vi.fn()} />);

    expect(enabledWith).toBe(false);
  });

  it('asks for it when they may', () => {
    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(enabledWith).toBe(true);
  });

  it('says plainly that nothing is waiting rather than rendering a blank panel', () => {
    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(screen.getByText('task.reviewQueue.empty.heading')).toBeTruthy();
  });

  it('says which phase the waiting time is measuring', () => {
    queueState = { isPending: false, isError: false, data: { tasks: [waiting()] } };

    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(screen.getByText('task.phase.REVIEW')).toBeTruthy();
  });

  it('reads an erased assignee as a former member rather than as a gap', () => {
    queueState = {
      isPending: false,
      isError: false,
      data: { tasks: [waiting({ assigneeName: '' })] },
    };

    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(screen.getByText('task.formerMember')).toBeTruthy();
  });

  it('opens the task it was asked to open', () => {
    const onOpen = vi.fn();
    queueState = { isPending: false, isError: false, data: { tasks: [waiting()] } };

    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={onOpen} />);
    fireEvent.click(screen.getByText('task.reviewQueue.open'));

    expect(onOpen).toHaveBeenCalledWith('task-1');
  });

  it('says so when it could not load, rather than showing an empty queue', () => {
    queueState = { isPending: false, isError: true, data: undefined };

    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(screen.getByText('task.reviewQueue.loadFailed')).toBeTruthy();
    expect(screen.queryByText('task.reviewQueue.empty.heading')).toBeNull();
  });

  it('shows that it is loading rather than an empty queue', () => {
    queueState = { isPending: true, isError: false, data: undefined };

    render(<ReviewQueue permissions={['TASK_REVIEW']} onOpen={vi.fn()} />);

    expect(screen.queryByText('task.reviewQueue.empty.heading')).toBeNull();
  });
});
