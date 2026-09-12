import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { STATE_TONE, type Tone } from '../features/canvas';
import { useInstancesInFull } from '../features/process';
import { chipState, useTasks, type TaskSummary } from '../features/task';
import { Avatar } from '../shared/ui/Avatar';
import { Card } from '../shared/ui/Card';
import { Chip } from '../shared/ui/Chip';
import { DeadlineIndicator } from '../shared/ui/DeadlineIndicator';
import { EmptyState } from '../shared/ui/EmptyState';
import type { TaskLifecycleState } from '../shared/ui/TaskActionRail';
import { TaskStateChip } from '../shared/ui/TaskStateChip';

export function PersonWorkSection({
  personId,
  displayName,
  onBack,
  onOpenTask,
  onOpenRun,
}: {
  personId: string;
  displayName: string;
  onBack: () => void;
  onOpenTask: (taskId: string) => void;
  onOpenRun: (instanceId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const tasks = useTasks();
  const runs = useInstancesInFull();

  const theirs = (tasks.data?.tasks ?? []).filter(
    (task: TaskSummary) => task.assigneeId === personId && task.state !== 'CLOSED',
  );
  const steering = runs.instances.filter(
    (run) => run.processOwnerId === personId && run.state === 'RUNNING',
  );

  const loading = tasks.isPending || runs.isPending;

  return (
    <section className="fo-task-page">
      <header className="fo-task-person-head">
        <button type="button" className="ui-button ui-button-quiet" onClick={onBack}>
          {t('chat.header.back')}
        </button>
        <Avatar id={personId} name={displayName} size={40} />
        <div>
          <h2 className="fo-task-person-name">{displayName}</h2>

          <p className="fo-task-person-scope">{t('person.scopeNote')}</p>
        </div>
      </header>

      {tasks.isError || runs.incomplete ? (
        <p role="alert" className="fo-task-page-error">
          <strong className="fo-task-page-error-line">{t('person.cannotRead')}</strong>
          {t('person.cannotReadBody')}
        </p>
      ) : null}

      {loading ? (
        <p className="fo-task-quiet">{t('chat.thread.loading')}</p>
      ) : (
        <>
          <section className="fo-task-section" aria-labelledby="person-holds">
            <header className="fo-task-section-head">
              <h3 id="person-holds" className="fo-task-section-title">
                {t('person.holds')}
              </h3>
            </header>
            {theirs.length === 0 ? (
              <EmptyState heading={t('person.noTasks')} />
            ) : (
              <ul className="fo-task-list">
                {theirs.map((task) => (
                  <li key={task.id}>
                    <button
                      type="button"
                      className="fo-task-row fo-task-row--wide"
                      onClick={() => onOpenTask(task.id)}
                    >
                      <span className="fo-task-row-title">{task.title}</span>
                      <TaskStateChip state={chipState(task.state)} />
                      <DeadlineIndicator dueAt={task.deadline} atRisk={task.atRisk} />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="fo-task-section" aria-labelledby="person-steers">
            <header className="fo-task-section-head">
              <h3 id="person-steers" className="fo-task-section-title">
                {t('person.steers')}
              </h3>
            </header>
            {steering.length === 0 ? (
              <EmptyState heading={t('person.noRuns')} />
            ) : (
              <div className="fo-task-run-list">
                {steering.map((run) => (
                  <Card
                    key={run.id}
                    title={run.name}
                    onOpen={() => onOpenRun(run.id)}
                    openLabel={t('person.openRun', { name: run.name })}
                    payload={<StepRail run={run} />}
                    meta={
                      <span className="fo-task-run-progress">
                        {t('person.progress', {
                          closed: run.progress.closed,
                          total: run.progress.total,
                        })}
                      </span>
                    }
                  />
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </section>
  );
}

function knownState(state: string | null): state is TaskLifecycleState {
  return state !== null && LIFECYCLE.includes(state as TaskLifecycleState);
}

const LIFECYCLE: readonly TaskLifecycleState[] = [
  'CREATED',
  'ACCEPTED',
  'IN_PROGRESS',
  'BLOCKED',
  'COMPLETED',
  'APPROVED',
  'CLOSED',
];

function StepRail({
  run,
}: {
  run: { steps: { id: string; title: string; taskState: string | null; position: number }[] };
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <span className="fo-task-step-rail">
      {[...run.steps]
        .sort((a, b) => a.position - b.position)
        .map((step) => {
          const tone: Tone = knownState(step.taskState)
            ? (STATE_TONE[chipState(step.taskState)] ?? 'neutral')
            : 'neutral';

          return (
            <span
              key={step.id}
              title={`${step.title} — ${step.taskState ?? t('person.planned')}`}
              className="fo-task-step"
            >
              <Chip tone={tone} dot>
                {step.position + 1}
              </Chip>
            </span>
          );
        })}
    </span>
  );
}
