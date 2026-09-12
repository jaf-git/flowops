import { useEffect, useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { DeadlineIndicator } from '../../../shared/ui/DeadlineIndicator';
import { Chip } from '../../../shared/ui/Chip';
import { RecordPage } from '../../../shared/ui/RecordPage';
import { Select } from '../../../shared/ui/Select';
import { TaskStateChip, type TaskState } from '../../../shared/ui/TaskStateChip';
import { useTaskActivity, useTaskDetail } from '../hooks/useTasks';
import { useFileTaskUnder, useTaskCategories } from '../hooks/useTaskCategories';
import { TaskActionsPanel } from './TaskActionsPanel';
import { TaskActivityPanel } from './TaskActivityPanel';
import { TaskMaterialPanel } from './TaskMaterialPanel';
import { Icon } from '../../../shared/ui/Icon';
import type { ActivityEntry } from '../api/taskApi';

const CHIP_STATE: Record<string, TaskState> = {
  CREATED: 'Created',
  ACCEPTED: 'Accepted',
  IN_PROGRESS: 'InProgress',
  BLOCKED: 'Blocked',
  COMPLETED: 'Completed',
  APPROVED: 'Approved',
  CLOSED: 'Closed',
};

interface TaskDrawerProps {
  taskId: string;

  viewerId: string;
  permissions: readonly string[];
  onClose: () => void;

  origin?: ReactNode;

  renderPipeline?: (subject: {
    taskId: string;
    title: string;
    state: string;
    templateId: string | null;
  }) => ReactNode;
}

export function TaskDrawer({
  taskId,
  viewerId,
  permissions,
  onClose,
  origin,
  renderPipeline,
}: TaskDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const detail = useTaskDetail(taskId);
  const [moved, setMoved] = useState<string | undefined>(undefined);

  const categories = useTaskCategories();
  const file = useFileTaskUnder();
  const groupings = categories.data?.categories ?? [];
  const filedUnder = categories.data?.filings[taskId] ?? '';
  const mayFile = permissions.includes('TASK_EDIT');

  const activity = useTaskActivity(taskId);

  useEffect(() => {
    function onKey(event: KeyboardEvent): void {
      if (event.key !== 'Escape') {
        return;
      }

      if (document.querySelector('dialog[open]') !== null) {
        return;
      }
      onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const task = detail.data;
  const mine = task !== undefined && task.assigneeId === viewerId;
  const directedByMe = task !== undefined && task.creatorId === viewerId;
  const blockedOn =
    task?.state === 'BLOCKED' ? latestBlockReason(activity.data?.entries ?? []) : undefined;

  const header = (
    <>
      <div className="fo-task-record-chips">
        {task !== undefined && CHIP_STATE[task.state] !== undefined ? (
          <TaskStateChip state={CHIP_STATE[task.state] as TaskState} />
        ) : null}

        {task?.atRisk === true ? (
          <Chip tone="at-risk">
            <span aria-hidden="true" className="ui-chip-state-glyph">
              {'●'}
            </span>
            {t('task.atRisk')}
          </Chip>
        ) : null}
        <button
          type="button"
          className="fo-icon-button fo-task-record-close"
          aria-label={t('task.drawer.close')}
          onClick={onClose}
        >
          <Icon name="close" size={11} strokeWidth={1.6} />
        </button>
      </div>
      <h2 className="fo-task-record-title">{task?.title ?? t('task.drawer.loading')}</h2>
    </>
  );

  const facts =
    task === undefined ? undefined : (
      <>
        <dl className="fo-drawer-fields">
          <dt>{t('task.drawer.assignee')}</dt>
          <dd>{task.assigneeName === '' ? t('task.formerMember') : task.assigneeName}</dd>
          <dt>{t('task.drawer.deadline')}</dt>
          <dd>
            {task.deadline === null ? (
              t('task.noDeadline')
            ) : (
              <DeadlineIndicator dueAt={task.deadline} atRisk={task.atRisk} blockedOn={blockedOn} />
            )}
          </dd>
          <dt>{t('task.drawer.priority')}</dt>
          <dd>{t(`task.priority.${task.priority}`)}</dd>

          <dt>{t('task.drawer.category')}</dt>
          <dd>
            {mayFile && groupings.length > 0 ? (
              <Select
                id={`task-group-${taskId}`}
                aria-label={t('task.drawer.category')}
                value={filedUnder}
                disabled={file.isPending}
                onChange={(event) =>
                  file.mutate({ taskId, categoryId: event.target.value || null })
                }
                options={[
                  { value: '', label: t('task.category.uncategorised') },
                  ...groupings.map((category) => ({ value: category.id, label: category.name })),
                ]}
              />
            ) : (
              (groupings.find((category) => category.id === filedUnder)?.name ??
              t('task.category.uncategorised'))
            )}
          </dd>
        </dl>

        {task.phases.length > 0 ? (
          <section>
            <h3 className="fo-eyebrow">{t('task.drawer.timeline')}</h3>
            <dl className="fo-drawer-meta">
              {task.phases.map((phase) => (
                <div key={phase.kind} style={{ display: 'contents' }}>
                  <dt>{t(`task.phase.${phase.kind}`)}</dt>
                  <dd>{formatDuration(phase.seconds)}</dd>
                </div>
              ))}
            </dl>
          </section>
        ) : null}
      </>
    );

  return (
    <aside
      role="dialog"
      aria-modal="false"
      aria-label={task?.title ?? t('task.drawer.loading')}
      className="fo-task-drawer fo-task-record-drawer"
    >
      <RecordPage header={header} facts={facts} factsLabel={t('task.drawer.facts')}>
        {detail.isError ? (
          <p role="alert" className="fo-task-page-error">
            {t('task.loadFailed')}
          </p>
        ) : task === undefined ? null : (
          <>
            {task.description !== null && task.description !== '' ? (
              <section>
                <h3 className="fo-eyebrow">{t('task.drawer.description')}</h3>
                <p className="fo-task-prose">{task.description}</p>
              </section>
            ) : null}

            {moved !== undefined ? (
              <p role="status" className="fo-task-moved">
                {moved}
              </p>
            ) : null}

            {origin === undefined ? null : <div>{origin}</div>}

            {renderPipeline?.({
              taskId: task.id,
              title: task.title,
              state: task.state,
              templateId: task.templateId,
            })}

            <TaskActionsPanel
              taskId={task.id}
              state={task.state}
              mine={mine}
              directedByMe={directedByMe}
              deadlineProposalOpen={task.deadlineProposal !== null}
              hasDeadline={task.deadline !== null}
              permissions={permissions}
              currentDeadline={task.deadline}
              currentAssigneeId={task.assigneeId}
              onMoved={setMoved}
            />

            <TaskMaterialPanel taskId={task.id} mine={mine} />

            <TaskActivityPanel taskId={task.id} closed={task.state === 'CLOSED'} />
          </>
        )}
      </RecordPage>
    </aside>
  );
}

function latestBlockReason(entries: readonly ActivityEntry[]): string | undefined {
  let latest: ActivityEntry | undefined;

  for (const entry of entries) {
    if (entry.kind !== 'TRANSITION' || entry.to !== 'BLOCKED') {
      continue;
    }
    if (entry.reason === null || entry.reason === '') {
      continue;
    }
    if (latest === undefined || Date.parse(entry.occurredAt) > Date.parse(latest.occurredAt)) {
      latest = entry;
    }
  }

  return latest?.reason ?? undefined;
}

function formatDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}
