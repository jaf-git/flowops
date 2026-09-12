// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { TaskReviewPanel } from './TaskReviewPanel';
import type { TaskDetail } from '../api/taskApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: unknown) =>
      typeof options === 'object' && options !== null && 'link' in options
        ? `${key}:${(options as { link: string }).link}`
        : key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const approveMutate = vi.fn();
const returnMutate = vi.fn();

let detailState: { isPending: boolean; isError: boolean; data: TaskDetail | undefined };
let approveState = { isPending: false, isError: false, error: undefined as unknown };
let returnState = { isPending: false, isError: false, error: undefined as unknown };

vi.mock('../hooks/useTasks', () => ({
  useTaskDetail: () => detailState,
  useApproveTask: () => ({ ...approveState, mutate: approveMutate }),
  useReturnTaskForRework: () => ({ ...returnState, mutate: returnMutate }),
}));

function detail(overrides: Partial<TaskDetail> = {}): TaskDetail {
  return {
    id: 'task-1',
    templateId: null,
    title: 'Draft the supplier review',
    description: null,
    assigneeId: 'andrei',
    assigneeName: 'Andrei Munteanu',
    creatorId: 'maria',
    deadline: '2026-09-01T09:00:00Z',
    priority: 'NORMAL',
    state: 'COMPLETED',
    selfAssigned: false,
    atRisk: false,
    deadlineProposal: null,
    createdAt: '2026-08-10T09:00:00Z',
    openPhase: 'REVIEW',
    phaseSince: '2026-08-11T15:00:00Z',
    phases: [
      { kind: 'WAIT', seconds: 7200 },
      { kind: 'ACTIVE', seconds: 10800 },
      { kind: 'REVIEW', seconds: 3600 },
    ],
    proof: {
      note: 'Compared both quarters and sent the summary to the board.',
      externalLink: null,
      submittedAt: '2026-08-11T15:00:00Z',
    },
    approval: null,
    completedAt: '2026-08-11T15:00:00Z',
    deadlineMet: true,
    ...overrides,
  };
}

function open(overrides: Partial<TaskDetail> = {}) {
  detailState = { isPending: false, isError: false, data: detail(overrides) };
  return render(
    <TaskReviewPanel taskId="task-1" onClose={vi.fn()} onApproved={vi.fn()} onReturned={vi.fn()} />,
  );
}

beforeEach(() => {
  detailState = { isPending: false, isError: false, data: detail() };
  approveState = { isPending: false, isError: false, error: undefined };
  returnState = { isPending: false, isError: false, error: undefined };
  approveMutate.mockReset();
  returnMutate.mockReset();
});

afterEach(cleanup);

describe('TaskReviewPanel', () => {
  it('shows the reviewer what was delivered', () => {
    open();

    expect(
      screen.getByText('Compared both quarters and sent the summary to the board.'),
    ).toBeTruthy();
  });

  it('says whether the deadline was met', () => {
    open();

    expect(screen.getByText('task.review.onTime')).toBeTruthy();
  });

  it('says so when it was not', () => {
    open({ deadlineMet: false });

    expect(screen.getByText('task.review.late')).toBeTruthy();
  });

  it('says the work has not been submitted rather than calling it late', () => {
    open({ deadlineMet: null, completedAt: null, proof: null });

    expect(screen.getByText('task.review.notSubmitted')).toBeTruthy();
    expect(screen.queryByText('task.review.late')).toBeNull();
  });

  it('approves with the score the reviewer chose', () => {
    open();

    fireEvent.change(screen.getByLabelText('task.review.field.score'), { target: { value: '5' } });
    fireEvent.click(screen.getByText('task.review.approve'));

    expect(approveMutate).toHaveBeenCalledTimes(1);
    expect(approveMutate.mock.calls[0]?.[0]).toMatchObject({ id: 'task-1', score: 5 });
  });

  it('offers no score control at all once the reviewer is sending the work back', () => {
    open();
    expect(screen.getByLabelText('task.review.field.score')).toBeTruthy();

    fireEvent.click(screen.getByText('task.review.switchToReturn'));

    expect(screen.queryByLabelText('task.review.field.score')).toBeNull();
  });

  it('sends the work back with the reason that was written', () => {
    open();
    fireEvent.click(screen.getByText('task.review.switchToReturn'));

    fireEvent.change(screen.getByLabelText('task.review.field.reason'), {
      target: { value: 'The figures for March are missing.' },
    });
    fireEvent.click(screen.getByText('task.review.return'));

    expect(returnMutate).toHaveBeenCalledTimes(1);
    expect(returnMutate.mock.calls[0]?.[0]).toMatchObject({
      id: 'task-1',
      reason: 'The figures for March are missing.',
    });
  });

  it('will not send work back with nothing to act on', () => {
    open();
    fireEvent.click(screen.getByText('task.review.switchToReturn'));

    expect(
      (screen.getByText('task.review.return').closest('button') as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('lets it go once a reason has been written', () => {
    open();
    fireEvent.click(screen.getByText('task.review.switchToReturn'));

    fireEvent.change(screen.getByLabelText('task.review.field.reason'), {
      target: { value: 'Needs the March figures.' },
    });

    expect(
      (screen.getByText('task.review.return').closest('button') as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('offers an http link as a link, with the opener isolated', () => {
    open({
      proof: {
        note: 'Done.',
        externalLink: 'https://drive.atelier.ro/q3',
        submittedAt: '2026-08-11T15:00:00Z',
      },
    });

    const link = screen.getByText('https://drive.atelier.ro/q3') as HTMLAnchorElement;
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe('https://drive.atelier.ro/q3');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('refuses to make a javascript: reference into something the reviewer can click', () => {
    open({
      proof: {
        note: 'Done.',
        externalLink: 'javascript:alert(document.cookie)',
        submittedAt: '2026-08-11T15:00:00Z',
      },
    });

    expect(screen.queryByRole('link')).toBeNull();
    expect(
      screen.getByText('task.review.linkNotFollowable:javascript:alert(document.cookie)'),
    ).toBeTruthy();
  });

  it('shows the phase breakdown rather than a single elapsed figure', () => {
    open();

    expect(screen.getByText('task.review.time')).toBeTruthy();
    expect(screen.queryByText(/total/i)).toBeNull();
  });

  it('says it is loading rather than showing an empty review', () => {
    detailState = { isPending: true, isError: false, data: undefined };

    render(
      <TaskReviewPanel
        taskId="task-1"
        onClose={vi.fn()}
        onApproved={vi.fn()}
        onReturned={vi.fn()}
      />,
    );

    expect(screen.getByText('task.review.loading')).toBeTruthy();
    expect(screen.queryByText('task.review.delivered')).toBeNull();
  });

  it('says why the server refused, in words the reviewer can act on', () => {
    approveState = {
      isPending: false,
      isError: true,
      error: new ApiError(403, { code: 'CANNOT_REVIEW_OWN_WORK' }),
    };

    open();

    expect(screen.getByText('task.review.error.CANNOT_REVIEW_OWN_WORK')).toBeTruthy();
  });

  it('shows a banner for a code it has no words for, rather than nothing', () => {
    approveState = {
      isPending: false,
      isError: true,
      error: new ApiError(500, { code: 'SOMETHING_NOBODY_ANTICIPATED' }),
    };

    open();

    expect(screen.getByText(/task\.review\.error\./)).toBeTruthy();
  });

  it('says nothing when nothing has gone wrong, so the banner means something', () => {
    open();

    expect(screen.queryByText('task.review.error.UNKNOWN')).toBeNull();
  });

  it('disables the approve control while its request is in flight', () => {
    approveState = { isPending: true, isError: false, error: undefined };

    open();

    const button = screen.getByText('task.review.approving').closest('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it('will not let the reviewer change their mind while a decision is in flight', () => {
    returnState = { isPending: true, isError: false, error: undefined };

    open();

    const button = screen
      .getByText('task.review.switchToReturn')
      .closest('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it('leaves them live when nothing is in flight, so disabled means something', () => {
    open();

    expect(
      (screen.getByText('task.review.approve').closest('button') as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('renders one segment per phase the task actually spent time in', () => {
    open();

    expect(screen.getByText(/ui\.phase\.active/)).toBeTruthy();
    expect(screen.getByText(/ui\.phase\.wait/)).toBeTruthy();
    expect(screen.getByText(/ui\.phase\.review/)).toBeTruthy();
  });

  it('says so when the task could not be loaded, rather than showing an empty review', () => {
    detailState = { isPending: false, isError: true, data: undefined };

    render(
      <TaskReviewPanel
        taskId="task-1"
        onClose={vi.fn()}
        onApproved={vi.fn()}
        onReturned={vi.fn()}
      />,
    );

    expect(screen.getByText('task.review.loadFailed')).toBeTruthy();
    expect(screen.queryByText('task.review.delivered')).toBeNull();
  });
});
