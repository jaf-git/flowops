import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { TaskActionRail, type TaskLifecycleState } from '../../../shared/ui/TaskActionRail';
import { useAcceptTask, useCloseTask, useStartTask, useUnblockTask } from '../hooks/useTasks';
import { BlockTaskDialog } from './BlockTaskDialog';
import { CompleteTaskDialog } from './CompleteTaskDialog';
import { ProposeDeadlineDialog } from './ProposeDeadlineDialog';
import { RejectTaskDialog } from './RejectTaskDialog';
import { OverrideTaskDialog } from './OverrideTaskDialog';
import { ReassignTaskDialog } from './ReassignTaskDialog';
import { SetDeadlineDialog } from './SetDeadlineDialog';
import { TaskReviewPanel } from './TaskReviewPanel';
import { DecideDeadlineDialogFromDetail, EditTaskDialogFromDetail } from './TaskDialogsFromDetail';

export interface TaskActionsPanelProps {
  taskId: string;
  state: TaskLifecycleState;

  mine: boolean;

  directedByMe: boolean;

  deadlineProposalOpen?: boolean;

  hasDeadline: boolean;

  permissions: readonly string[];

  currentDeadline?: string | null;

  currentAssigneeId?: string | null;

  onMoved?: (message: string) => void;
}

export function TaskActionsPanel({
  taskId,
  state,
  mine,
  directedByMe,
  deadlineProposalOpen = false,
  hasDeadline,
  permissions,
  currentDeadline = null,
  currentAssigneeId = null,
  onMoved,
}: TaskActionsPanelProps): JSX.Element {
  const { t } = useTranslation();

  const accept = useAcceptTask();
  const start = useStartTask();
  const unblock = useUnblockTask();
  const closeTask = useCloseTask();

  const [open, setOpen] = useState<
    | 'block'
    | 'complete'
    | 'reject'
    | 'propose'
    | 'decide'
    | 'edit'
    | 'deadline'
    | 'review'
    | 'reassign'
    | 'override'
  >();

  const moved = (message: string) => (): void => onMoved?.(message);

  const busy = accept.isPending || start.isPending || unblock.isPending || closeTask.isPending;

  const refusal = [accept, start, unblock, closeTask].find((move) => move.error != null)?.error;

  return (
    <div style={{ display: 'grid', gap: 'var(--gap-3)' }}>
      {refusal != null && (
        <Banner tone="alert">
          {refusal instanceof ApiError ? refusal.message : t('task.moved.failed')}
        </Banner>
      )}

      <TaskActionRail
        state={state}
        mine={mine}
        directedByMe={directedByMe}
        deadlineProposalOpen={deadlineProposalOpen}
        hasDeadline={hasDeadline}
        permissions={permissions}
        busy={busy}

        onAccept={() => accept.mutate(taskId)}
        onReject={() => setOpen('reject')}
        onPropose={() => setOpen('propose')}
        onSetDeadline={() => setOpen('deadline')}
        onDecideDeadline={() => setOpen('decide')}
        onEdit={() => setOpen('edit')}
        onStart={() => start.mutate(taskId, { onSuccess: moved(t('task.moved.started')) })}
        onBlock={() => setOpen('block')}
        onUnblock={() =>
          unblock.mutate(
            { id: taskId, resolution: '' },
            { onSuccess: moved(t('task.moved.unblocked')) },
          )
        }
        onComplete={() => setOpen('complete')}
        onReview={() => setOpen('review')}
        onClose={() => closeTask.mutate(taskId, { onSuccess: moved(t('task.moved.closed')) })}
        onReassign={() => setOpen('reassign')}
        onOverride={() => setOpen('override')}
      />

      {open === 'block' && (
        <BlockTaskDialog
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onBlocked={moved(t('task.moved.blocked'))}
        />
      )}

      {open === 'complete' && (
        <CompleteTaskDialog
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onCompleted={moved(t('task.moved.completed'))}
        />
      )}

      {open === 'reject' && (
        <RejectTaskDialog
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onRejected={moved(t('task.moved.rejected'))}
        />
      )}

      {open === 'propose' && (
        <ProposeDeadlineDialog
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onProposed={moved(t('task.moved.deadlineProposed'))}
        />
      )}

      {open === 'deadline' && (
        <SetDeadlineDialog
          key={taskId}
          taskId={taskId}
          currentDeadline={currentDeadline}
          onClose={() => setOpen(undefined)}
          onSet={moved(t('task.moved.deadlineSet'))}
        />
      )}

      {open === 'decide' && (
        <DecideDeadlineDialogFromDetail
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onDecided={moved(t('task.moved.deadlineDecided'))}
        />
      )}

      {open === 'edit' && (
        <EditTaskDialogFromDetail
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onEdited={moved(t('task.moved.edited'))}
        />
      )}

      {open === 'reassign' && (
        <ReassignTaskDialog
          key={taskId}
          taskId={taskId}
          currentAssigneeId={currentAssigneeId}
          onClose={() => setOpen(undefined)}
          onReassigned={moved(t('task.moved.reassigned'))}
        />
      )}

      {open === 'override' && (
        <OverrideTaskDialog
          key={taskId}
          taskId={taskId}
          state={state}
          onClose={() => setOpen(undefined)}
          onOverridden={moved(t('task.moved.overridden'))}
        />
      )}

      {open === 'review' && (
        <TaskReviewPanel
          key={taskId}
          taskId={taskId}
          onClose={() => setOpen(undefined)}
          onApproved={moved(t('task.moved.approved'))}
          onReturned={moved(t('task.moved.returned'))}
        />
      )}
    </div>
  );
}
