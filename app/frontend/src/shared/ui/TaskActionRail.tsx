import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from './Button';

type Weight = 'forward' | 'aside' | 'undoing';

interface RailAction {
  key: string;
  label: string;
  submitting: string;
  weight: Weight;
  run: () => void;
  disabled?: boolean;
  hint?: string;
}

export type TaskLifecycleState =
  'CREATED' | 'ACCEPTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'APPROVED' | 'CLOSED';

interface TaskActionRailProps {
  state: TaskLifecycleState;

  mine: boolean;

  directedByMe: boolean;

  deadlineProposalOpen: boolean;

  hasDeadline: boolean;

  permissions: readonly string[];

  busy: boolean;
  onAccept: () => void;
  onReject: () => void;
  onPropose: () => void;
  onSetDeadline: () => void;
  onDecideDeadline: () => void;
  onEdit: () => void;
  onStart: () => void;
  onBlock: () => void;
  onUnblock: () => void;
  onComplete: () => void;
  onReview: () => void;
  onClose: () => void;

  onReassign: () => void;

  onOverride: () => void;

  scope?: 'full' | 'queue';
}

export function TaskActionRail({
  state,
  mine,
  directedByMe,
  deadlineProposalOpen,
  hasDeadline,
  permissions,
  busy,
  onAccept,
  onSetDeadline,
  onReject,
  onPropose,
  onDecideDeadline,
  onEdit,
  onStart,
  onBlock,
  onUnblock,
  onComplete,
  onReview,
  onClose,
  onReassign,
  onOverride,
  scope = 'full',
}: TaskActionRailProps): JSX.Element | null {
  const { t } = useTranslation();

  const actions: RailAction[] = [];

  const consequential = scope === 'full';

  if (state === 'COMPLETED' && !mine && permissions.includes('TASK_REVIEW')) {
    actions.push({
      key: 'review',
      label: t('task.review.action'),
      submitting: t('task.review.action'),
      weight: 'forward',
      run: onReview,
    });
  }
  if (consequential && state === 'APPROVED' && permissions.includes('TASK_CLOSE')) {
    actions.push({
      key: 'close',
      label: t('task.close.action'),
      submitting: t('task.close.submitting'),
      weight: 'forward',
      run: onClose,
    });
  }

  if (deadlineProposalOpen && directedByMe && permissions.includes('TASK_DECIDE_DEADLINE')) {
    actions.push({
      key: 'decide-deadline',
      label: t('task.decideDeadline.action'),
      submitting: t('task.decideDeadline.submitting'),
      weight: 'forward',
      run: onDecideDeadline,
    });
  }
  if (consequential && state !== 'CLOSED' && directedByMe && permissions.includes('TASK_EDIT')) {
    actions.push({
      key: 'edit',
      label: t('task.edit.action'),
      submitting: t('task.edit.submitting'),
      weight: 'aside',
      run: onEdit,
    });
  }

  if (
    consequential &&
    state !== 'CLOSED' &&
    state !== 'COMPLETED' &&
    permissions.includes('TASK_REASSIGN')
  ) {
    actions.push({
      key: 'reassign',
      label: t('task.reassign.action'),
      submitting: t('task.reassign.submitting'),
      weight: 'aside',
      run: onReassign,
    });
  }

  if (consequential && permissions.includes('TASK_OVERRIDE')) {
    actions.push({
      key: 'override',
      label: t('task.override.action'),
      submitting: t('task.override.submitting'),
      weight: 'undoing',
      run: onOverride,
    });
  }

  if (!mine) {
    return actions.length === 0 ? null : rail(actions, busy);
  }

  if (state === 'CREATED') {
    actions.push(
      {
        key: 'accept',
        label: t('task.accept.action'),
        submitting: t('task.accept.submitting'),
        weight: 'forward',
        run: onAccept,
      },

      {
        key: 'reject',
        label: t('task.reject.action'),
        submitting: t('task.reject.submitting'),
        weight: 'undoing',
        run: onReject,
      },
    );
  }
  if (state === 'ACCEPTED') {
    actions.push({
      key: 'set-deadline',
      label: t('task.setDeadline.action'),
      submitting: t('task.setDeadline.submitting'),
      weight: 'aside',
      run: onSetDeadline,
    });

    actions.push({
      key: 'start',
      label: t('task.start.action'),
      submitting: t('task.start.submitting'),
      weight: 'forward',
      run: onStart,
      disabled: !hasDeadline,
      hint: hasDeadline ? undefined : t('task.setDeadline.startNeedsADate'),
    });
  }
  if (state === 'IN_PROGRESS') {
    actions.push(
      {
        key: 'block',
        label: t('task.block.action'),
        submitting: t('task.block.submitting'),
        weight: 'aside',
        run: onBlock,
      },
      {
        key: 'complete',
        label: t('task.complete.action'),
        submitting: t('task.complete.submitting'),
        weight: 'forward',
        run: onComplete,
      },

      {
        key: 'propose-deadline',
        label: t('task.proposeDeadline.action'),
        submitting: t('task.proposeDeadline.submitting'),
        weight: 'aside',
        run: onPropose,
      },
    );
  }
  if (state === 'BLOCKED') {
    actions.push({
      key: 'unblock',
      label: t('task.unblock.action'),
      submitting: t('task.unblock.submitting'),
      weight: 'forward',
      run: onUnblock,
    });
  }

  if (actions.length === 0) {
    return null;
  }

  return rail(actions, busy);
}

function rail(actions: RailAction[], busy: boolean): JSX.Element {
  const live = actions.filter((action) => action.disabled !== true);
  const primary = (
    live.find((action) => action.weight === 'forward') ??
    live.find((action) => action.weight === 'aside')
  )?.key;

  return (
    <div className="ui-rail-actions">
      {actions.map((action) => (
        <Button
          key={action.key}
          variant={
            action.key === primary
              ? 'primary'
              : action.weight === 'undoing'
                ? 'destructive'
                : 'secondary'
          }
          onClick={action.run}
          loading={busy}
          loadingLabel={action.submitting}
          disabled={action.disabled}
          title={action.hint}
        >
          {action.label}
        </Button>
      ))}
    </div>
  );
}
