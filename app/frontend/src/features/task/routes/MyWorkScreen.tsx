import { useMemo, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Chip } from '../../../shared/ui/Chip';
import { Select } from '../../../shared/ui/Select';
import { TaskStateChip, type TaskState } from '../../../shared/ui/TaskStateChip';
import { useTasks } from '../hooks/useTasks';
import type { ApiTaskState, TaskSummary } from '../api/taskApi';
import { PageHeader } from '../../../shared/ui/PageHeader';

const CHIP_STATE: Record<string, TaskState> = {
  CREATED: 'Created',
  ACCEPTED: 'Accepted',
  IN_PROGRESS: 'InProgress',
  BLOCKED: 'Blocked',
  COMPLETED: 'Completed',
  APPROVED: 'Approved',
  CLOSED: 'Closed',
};

const FILTERABLE: readonly ApiTaskState[] = [
  'CREATED',
  'ACCEPTED',
  'IN_PROGRESS',
  'BLOCKED',
  'COMPLETED',
  'APPROVED',
  'CLOSED',
];

interface MyWorkScreenProps {
  onOpenTask: (taskId: string) => void;
}

export function MyWorkScreen({ onOpenTask }: MyWorkScreenProps): JSX.Element {
  const { t } = useTranslation();
  const tasks = useTasks();
  const [state, setState] = useState<ApiTaskState | 'ALL'>('ALL');

  const mine = useMemo(() => {
    const own = (tasks.data?.tasks ?? []).filter((task) => task.mine);
    return state === 'ALL' ? own : own.filter((task) => task.state === state);
  }, [tasks.data?.tasks, state]);

  if (tasks.isError) {
    return (
      <div className="fo-task-page">
        <PageHeader title={t('myWork.title')} subtitle={t('myWork.subtitle')} />
        <p role="alert" className="fo-task-page-error">
          <strong className="fo-task-page-error-line">{t('myWork.cannotRead')}</strong>
          {t('myWork.cannotReadBody')}
        </p>
      </div>
    );
  }

  return (
    <div className="fo-task-page">
      <PageHeader
        title={t('myWork.title')}
        subtitle={t('myWork.subtitle')}
        actions={
          <span className="fo-task-filter-choice">
            <label className="fo-task-filter-label" htmlFor="my-work-state">
              {t('myWork.filter.state')}
            </label>
            <Select
              id="my-work-state"
              value={state}
              onChange={(event) => setState(event.target.value as ApiTaskState | 'ALL')}
              options={[
                { value: 'ALL', label: t('myWork.filter.all') },
                ...FILTERABLE.map((candidate) => ({
                  value: candidate,
                  label: t(`task.state.${candidate}`),
                })),
              ]}
            />
          </span>
        }
      />

      {mine.length === 0 ? (
        <p className="fo-task-quiet">
          {state === 'ALL' ? t('myWork.empty.none') : t('myWork.empty.filtered')}
        </p>
      ) : (
        <ul className="fo-task-list">
          {mine.map((task) => (
            <li key={task.id}>
              <MyWorkRow task={task} onOpen={() => onOpenTask(task.id)} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function MyWorkRow({ task, onOpen }: { task: TaskSummary; onOpen: () => void }): JSX.Element {
  const { t } = useTranslation();

  return (
    <button type="button" className="fo-task-row" onClick={onOpen}>
      <span className="fo-task-row-title">{task.title}</span>

      {task.atRisk ? (
        <Chip tone="at-risk">
          <span aria-hidden="true" className="ui-chip-state-glyph">
            {'●'}
          </span>
          {t('task.atRisk')}
        </Chip>
      ) : (
        <span />
      )}
      {CHIP_STATE[task.state] === undefined ? null : (
        <TaskStateChip state={CHIP_STATE[task.state] as TaskState} />
      )}
      <span className="fo-task-row-when">
        {task.deadline === null
          ? t('task.noDeadline')
          : new Date(task.deadline).toLocaleDateString()}
      </span>
    </button>
  );
}
