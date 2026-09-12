// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { TaskActionRail, type TaskLifecycleState } from './TaskActionRail';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const handlers = {
  onAccept: vi.fn(),
  onStart: vi.fn(),
  onBlock: vi.fn(),
  onUnblock: vi.fn(),
  onComplete: vi.fn(),
  onReview: vi.fn(),
  onClose: vi.fn(),
  onReject: vi.fn(),
  onPropose: vi.fn(),
  onSetDeadline: vi.fn(),
  onDecideDeadline: vi.fn(),
  onEdit: vi.fn(),
  onReassign: vi.fn(),
  onOverride: vi.fn(),
};

const ASSIGNEE_PERMISSIONS = ['TASK_VIEW_OWN', 'TASK_ACT_OWN'];
const REVIEWER_PERMISSIONS = ['TASK_VIEW_OWN', 'TASK_ACT_OWN', 'TASK_REVIEW', 'TASK_CLOSE'];

function renderRail(
  state: TaskLifecycleState,
  options: {
    mine?: boolean;
    busy?: boolean;
    permissions?: string[];
    directedByMe?: boolean;
    deadlineProposalOpen?: boolean;

    hasDeadline?: boolean;
    scope?: 'full' | 'queue';
  } = {},
) {
  return render(
    <TaskActionRail
      state={state}
      mine={options.mine ?? true}
      directedByMe={options.directedByMe ?? false}
      deadlineProposalOpen={options.deadlineProposalOpen ?? false}
      hasDeadline={options.hasDeadline ?? true}
      permissions={options.permissions ?? ASSIGNEE_PERMISSIONS}
      busy={options.busy ?? false}
      scope={options.scope ?? 'full'}
      {...handlers}
    />,
  );
}

function offered(): string[] {
  return screen.queryAllByRole('button').map((button) => button.textContent ?? '');
}

afterEach(() => {
  cleanup();
  Object.values(handlers).forEach((handler) => handler.mockReset());
});

describe('the action rail', () => {
  it('offers the two answers from Created: take it or decline it', () => {
    renderRail('CREATED');

    expect(offered()).toEqual(['task.accept.action', 'task.reject.action']);
  });

  it('offers nobody else the assignee-only answers on work that is not theirs', () => {
    renderRail('CREATED', { mine: false, permissions: REVIEWER_PERMISSIONS });

    expect(offered()).not.toContain('task.reject.action');
    expect(offered()).not.toContain('task.proposeDeadline.action');
  });

  it('offers Edit to the person who assigned the work and to nobody else', () => {
    renderRail('CREATED', {
      mine: false,
      directedByMe: true,
      permissions: [...REVIEWER_PERMISSIONS, 'TASK_EDIT'],
    });
    expect(offered()).toContain('task.edit.action');
  });

  it('withholds Edit from somebody who holds the permission and did not assign the work', () => {
    renderRail('CREATED', {
      mine: false,
      directedByMe: false,
      permissions: [...REVIEWER_PERMISSIONS, 'TASK_EDIT'],
    });
    expect(offered()).not.toContain('task.edit.action');
  });

  it('withholds Edit once the task is closed', () => {
    renderRail('CLOSED', {
      mine: false,
      directedByMe: true,
      permissions: [...REVIEWER_PERMISSIONS, 'TASK_EDIT'],
    });
    expect(offered()).not.toContain('task.edit.action');
  });

  it('offers the date decision only while a proposal is open', () => {
    renderRail('CREATED', {
      mine: false,
      directedByMe: true,
      deadlineProposalOpen: true,
      permissions: [...REVIEWER_PERMISSIONS, 'TASK_DECIDE_DEADLINE'],
    });
    expect(offered()).toContain('task.decideDeadline.action');
  });

  it('withholds the date decision when nothing is waiting for an answer', () => {
    renderRail('CREATED', {
      mine: false,
      directedByMe: true,
      deadlineProposalOpen: false,
      permissions: [...REVIEWER_PERMISSIONS, 'TASK_DECIDE_DEADLINE'],
    });
    expect(offered()).not.toContain('task.decideDeadline.action');
  });

  it('withholds the date decision from somebody who does not hold the permission', () => {
    renderRail('CREATED', {
      mine: false,
      directedByMe: true,
      deadlineProposalOpen: true,
      permissions: REVIEWER_PERMISSIONS,
    });
    expect(offered()).not.toContain('task.decideDeadline.action');
  });

  it('offers the date and then beginning, from Accepted', () => {
    renderRail('ACCEPTED');

    expect(offered()).toEqual(['task.setDeadline.action', 'task.start.action']);
  });

  it('will not let work begin before a date exists, and says why', () => {
    renderRail('ACCEPTED', { hasDeadline: false });

    const start = screen.getByRole('button', { name: 'task.start.action' }) as HTMLButtonElement;
    expect(start.disabled).toBe(true);
    expect(start.getAttribute('title')).toBe('task.setDeadline.startNeedsADate');

    const setDate = screen.getByRole('button', {
      name: 'task.setDeadline.action',
    }) as HTMLButtonElement;
    expect(setDate.disabled).toBe(false);
  });

  it('offers stopping, finishing and asking for another date from InProgress', () => {
    renderRail('IN_PROGRESS');

    expect(offered()).toEqual([
      'task.block.action',
      'task.complete.action',
      'task.proposeDeadline.action',
    ]);
  });

  it('offers only carrying on from Blocked, because finishing blocked work means unblocking first', () => {
    renderRail('BLOCKED');

    expect(offered()).toEqual(['task.unblock.action']);
  });

  it.each<TaskLifecycleState>(['COMPLETED', 'APPROVED', 'CLOSED'])(
    'offers the assignee nothing at all once the work has left them: %s',
    (state) => {
      renderRail(state);

      expect(offered()).toEqual([]);
    },
  );

  it('offers nothing on work that is not the viewer own, in a state that would offer two', () => {
    renderRail('IN_PROGRESS', { mine: false });

    expect(offered()).toEqual([]);
  });

  it('runs the action it names', () => {
    renderRail('IN_PROGRESS');

    fireEvent.click(screen.getByText('task.block.action'));

    expect(handlers.onBlock).toHaveBeenCalledTimes(1);
    expect(handlers.onComplete).not.toHaveBeenCalled();
  });

  it('disables every action while a request for this task is in flight', () => {
    renderRail('IN_PROGRESS', { busy: true });

    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(3);
    buttons.forEach((button) => expect((button as HTMLButtonElement).disabled).toBe(true));
  });

  it('leaves them live when nothing is in flight, so disabled means something', () => {
    renderRail('IN_PROGRESS');

    screen
      .getAllByRole('button')
      .forEach((button) => expect((button as HTMLButtonElement).disabled).toBe(false));
  });

  describe('for somebody who is not the assignee', () => {
    it('offers Review on completed work to a reviewer', () => {
      renderRail('COMPLETED', { mine: false, permissions: REVIEWER_PERMISSIONS });

      expect(offered()).toEqual(['task.review.action']);
    });

    it('offers nothing on completed work to somebody without TASK_REVIEW', () => {
      renderRail('COMPLETED', { mine: false, permissions: ASSIGNEE_PERMISSIONS });

      expect(offered()).toEqual([]);
    });

    it('offers Close on approved work to somebody who may close it', () => {
      renderRail('APPROVED', { mine: false, permissions: REVIEWER_PERMISSIONS });

      expect(offered()).toEqual(['task.close.action']);
    });

    it('offers nothing on approved work to somebody without TASK_CLOSE', () => {
      renderRail('APPROVED', { mine: false, permissions: ASSIGNEE_PERMISSIONS });

      expect(offered()).toEqual([]);
    });

    it('runs the handler the button names', () => {
      renderRail('COMPLETED', { mine: false, permissions: REVIEWER_PERMISSIONS });

      fireEvent.click(screen.getByText('task.review.action'));

      expect(handlers.onReview).toHaveBeenCalledTimes(1);
      expect(handlers.onClose).not.toHaveBeenCalled();
    });
  });

  it('offers a reviewer no judgement on their own completed work, however much they hold', () => {
    renderRail('COMPLETED', { mine: true, permissions: REVIEWER_PERMISSIONS });

    expect(offered()).toEqual([]);
  });

  it('offers Close on your own approved work, because closing judges nobody', () => {
    renderRail('APPROVED', { mine: true, permissions: REVIEWER_PERMISSIONS });

    expect(offered()).toEqual(['task.close.action']);
  });

  it.each(['CREATED', 'ACCEPTED', 'IN_PROGRESS', 'BLOCKED', 'CLOSED'] as const)(
    'offers a reviewer nothing on somebody else work in %s',
    (state) => {
      renderRail(state, { mine: false, permissions: REVIEWER_PERMISSIONS });

      expect(offered()).toEqual([]);
    },
  );

  describe('in a queue', () => {
    const OWNER = [
      'TASK_VIEW_OWN',
      'TASK_ACT_OWN',
      'TASK_REVIEW',
      'TASK_CLOSE',
      'TASK_EDIT',
      'TASK_REASSIGN',
      'TASK_OVERRIDE',
      'TASK_DECIDE_DEADLINE',
    ];

    it('withholds the four that are rare or change who is accountable', () => {
      renderRail('IN_PROGRESS', {
        mine: false,
        directedByMe: true,
        permissions: OWNER,
        scope: 'queue',
      });

      expect(offered()).not.toContain('task.edit.action');
      expect(offered()).not.toContain('task.reassign.action');
      expect(offered()).not.toContain('task.override.action');
    });

    it('withholds Close, which the full rail offers on the same task', () => {
      const withheld = { mine: false, permissions: OWNER, scope: 'queue' } as const;
      renderRail('APPROVED', withheld);
      expect(offered()).toEqual([]);

      cleanup();
      renderRail('APPROVED', { mine: false, permissions: OWNER });
      expect(offered()).toContain('task.close.action');
    });

    it('keeps answering a proposed date, because somebody is waiting on it', () => {
      renderRail('IN_PROGRESS', {
        mine: false,
        directedByMe: true,
        deadlineProposalOpen: true,
        permissions: OWNER,
        scope: 'queue',
      });

      expect(offered()).toContain('task.decideDeadline.action');
    });

    it('keeps opening a review, and keeps the assignee their own work', () => {
      renderRail('COMPLETED', { mine: false, permissions: OWNER, scope: 'queue' });
      expect(offered()).toContain('task.review.action');

      cleanup();
      renderRail('CREATED', { mine: true, permissions: OWNER, scope: 'queue' });
      expect(offered()).toEqual(['task.accept.action', 'task.reject.action']);
    });
  });
});
