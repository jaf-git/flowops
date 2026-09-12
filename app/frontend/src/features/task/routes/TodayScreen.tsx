import { useMemo, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { BentoGrid, BentoTile } from '../../../shared/ui/BentoGrid';
import { Card } from '../../../shared/ui/Card';
import { TaskStateChip, type TaskState } from '../../../shared/ui/TaskStateChip';
import { useTasks } from '../hooks/useTasks';
import type { TaskSummary } from '../api/taskApi';
import { todayGroups, type TodayGroup } from '../model/today';
import { PageHeader } from '../../../shared/ui/PageHeader';
import { CountUp } from '../../../shared/motion/CountUp';

const CHIP_STATE: Record<string, TaskState> = {
  CREATED: 'Created',
  ACCEPTED: 'Accepted',
  IN_PROGRESS: 'InProgress',
  BLOCKED: 'Blocked',
  COMPLETED: 'Completed',
  APPROVED: 'Approved',
  CLOSED: 'Closed',
};

interface TodayScreenProps {
  onOpenTask: (taskId: string) => void;
}

export function TodayScreen({ onOpenTask }: TodayScreenProps): JSX.Element {
  const { t } = useTranslation();
  const tasks = useTasks();

  const groups = useMemo(
    () => todayGroups(tasks.data?.tasks ?? [], new Date()),
    [tasks.data?.tasks],
  );

  if (tasks.isError) {
    return (
      <div className="fo-task-page">
        <PageHeader title={t('today.title')} subtitle={t('today.subtitle')} />
        <p role="alert" className="fo-task-page-error">
          <strong className="fo-task-page-error-line">{t('today.cannotRead')}</strong>
          {t('today.cannotReadBody')}
        </p>
      </div>
    );
  }

  return (
    <div className="fo-task-page">
      <PageHeader title={t('today.title')} subtitle={t('today.subtitle')} />

      <BentoGrid>
        {groups.map(({ group, tasks: inGroup }) => (
          <BentoTile key={group} span={4}>
            <Card
              className={group === 'attention' ? 'fo-task-tile fo-task-tile--lead' : 'fo-task-tile'}
              eyebrow={t(`today.group.${group}`)}
              payload={
                <span className="fo-task-tile-figure">
                  <CountUp value={inGroup.length} format={(held) => held.toLocaleString()} />
                </span>
              }
              detail={inGroup.length === 0 ? t(`today.empty.${group}`) : t(`today.tile.${group}`)}
            />
          </BentoTile>
        ))}
      </BentoGrid>

      {groups.map(({ group, tasks: inGroup }) => (
        <TodaySection key={group} group={group} tasks={inGroup} onOpenTask={onOpenTask} />
      ))}
    </div>
  );
}

function TodaySection({
  group,
  tasks,
  onOpenTask,
}: {
  group: TodayGroup;
  tasks: readonly TaskSummary[];
  onOpenTask: (taskId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="fo-task-section" aria-labelledby={`today-${group}`}>
      <header className="fo-task-section-head">
        <h2 id={`today-${group}`} className="fo-task-section-title">
          {t(`today.group.${group}`)}
        </h2>
        <span className="fo-task-section-count">{tasks.length.toLocaleString()}</span>
      </header>

      {tasks.length === 0 ? (
        <p className="fo-task-quiet">{t(`today.empty.${group}`)}</p>
      ) : (
        <ul className="fo-task-list">
          {tasks.map((task) => (
            <li key={task.id}>
              <TodayRow task={task} onOpen={() => onOpenTask(task.id)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function TodayRow({ task, onOpen }: { task: TaskSummary; onOpen: () => void }): JSX.Element {
  const { t } = useTranslation();

  return (
    <button type="button" className="fo-task-row" onClick={onOpen}>
      <span className="fo-task-row-title">{task.title}</span>
      <span className="fo-task-row-person">
        {task.assigneeName === '' ? t('task.formerMember') : task.assigneeName}
      </span>
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
