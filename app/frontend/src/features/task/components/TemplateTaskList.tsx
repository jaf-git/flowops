import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { DeadlineIndicator } from '../../../shared/ui/DeadlineIndicator';
import { Spinner } from '../../../shared/ui/Spinner';
import { TaskStateChip } from '../../../shared/ui/TaskStateChip';
import { chipState, type TemplateTaskRow } from '../api/taskApi';

interface TemplateTaskListProps {
  rows: readonly TemplateTaskRow[];
  loading: boolean;

  onOpenTask: (taskId: string) => void;

  canvasHrefFor: (taskId: string) => string | undefined;
}

export function TemplateTaskList({
  rows,
  loading,
  onOpenTask,
  canvasHrefFor,
}: TemplateTaskListProps): JSX.Element {
  const { t, i18n } = useTranslation();

  if (loading) {
    return (
      <div className="fo-template-tasks">
        <Spinner label={t('task.loading')} />
      </div>
    );
  }

  if (rows.length === 0) {
    return <p className="fo-usage-none">{t('tasklib.usage.live.movedOn')}</p>;
  }

  return (
    <ul className="fo-template-tasks">
      {rows.map((row) => {
        const canvas = canvasHrefFor(row.taskId);
        return (
          <li className="fo-template-task" key={row.taskId}>
            <button
              type="button"
              className="fo-template-task-title"
              onClick={() => onOpenTask(row.taskId)}
              title={t('tasklib.usage.live.openTask', { title: row.title })}
            >
              {row.title}
            </button>

            <span className="fo-template-task-person">
              {row.assigneeName ?? t('task.formerMember')}
            </span>

            <TaskStateChip state={chipState(row.state)} />

            <span className="fo-template-task-due">
              {row.state === 'CLOSED' ? (
                <span style={{ color: 'var(--ink-200)' }}>{'—'}</span>
              ) : (
                <DeadlineIndicator dueAt={row.deadline} />
              )}
            </span>

            {canvas === undefined ? (
              <span className="fo-template-task-canvas" aria-hidden="true">
                {'—'}
              </span>
            ) : (
              <Link
                className="fo-template-task-canvas"
                to={canvas}
                lang={i18n.language}
                title={t('tasklib.usage.live.openCanvas', { title: row.title })}
              >
                {'↗'}
              </Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}
