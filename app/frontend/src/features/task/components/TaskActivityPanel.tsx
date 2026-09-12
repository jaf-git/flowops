import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Textarea } from '../../../shared/ui/Textarea';
import { useCommentOnTask, useTaskActivity } from '../hooks/useTasks';
import type { ActivityEntry } from '../api/taskApi';

interface TaskActivityPanelProps {
  taskId: string;

  closed: boolean;
}

export function TaskActivityPanel({ taskId, closed }: TaskActivityPanelProps): JSX.Element {
  const { t } = useTranslation();
  const activity = useTaskActivity(taskId);
  const comment = useCommentOnTask();
  const [body, setBody] = useState('');

  function post(): void {
    comment.mutate(
      { id: taskId, body },
      {
        onSuccess: () => {
          comment.reset();
          setBody('');
        },
      },
    );
  }

  const entries = activity.data?.entries ?? [];
  const refusal = comment.error instanceof ApiError ? comment.error.message : undefined;

  return (
    <section>
      <h3 className="fo-eyebrow">{t('task.activity.title')}</h3>

      {activity.isError ? (
        <p role="alert" style={{ margin: 0, color: 'var(--on-critical)' }}>
          {t('task.activity.loadFailed')}
        </p>
      ) : entries.length === 0 ? (
        <p style={{ margin: 0, color: 'var(--ink-700)', fontSize: 'var(--text-body)' }}>
          {t('task.activity.empty')}
        </p>
      ) : (
        <ol className="fo-activity">
          {entries.map((entry, position) => (
            <li key={position} className="fo-activity-row">
              <ActivityRow entry={entry} />
            </li>
          ))}
        </ol>
      )}

      {!closed && (
        <div style={{ display: 'grid', gap: 'var(--gap-2)', marginBlockStart: 'var(--gap-4)' }}>
          {refusal !== undefined && <Banner tone="alert">{refusal}</Banner>}
          <Textarea
            id={`comment-${taskId}`}
            aria-label={t('task.activity.compose')}
            placeholder={t('task.activity.placeholder')}
            value={body}
            onChange={(event) => setBody(event.target.value)}
            maxLength={4000}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              onClick={post}
              disabled={body.trim() === ''}
              loading={comment.isPending}
              loadingLabel={t('task.activity.posting')}
            >
              {t('task.activity.post')}
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}

function ActivityRow({ entry }: { entry: ActivityEntry }): JSX.Element {
  const { t } = useTranslation();

  const who = entry.actorName === '' ? t('task.formerMember') : entry.actorName;
  const when = new Date(entry.occurredAt).toLocaleString();

  if (entry.kind === 'COMMENT') {
    return (
      <>
        <p className="fo-activity-head">
          <strong>{who}</strong> <span>{when}</span>
        </p>
        <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>{entry.body}</p>
      </>
    );
  }

  return (
    <>
      <p className="fo-activity-head">
        <strong>{who}</strong>{' '}
        <span>
          {entry.from === null
            ? t('task.activity.created')
            : t('task.activity.moved', {
                from: t(`task.state.${entry.from}`),
                to: t(`task.state.${entry.to}`),
              })}
        </span>{' '}
        <span>{when}</span>
        {entry.overridden && (
          <span className="fo-activity-forced">{t('task.activity.forced')}</span>
        )}
      </p>
      {entry.reason !== null && (
        <p
          style={{
            margin: 0,
            color: 'var(--ink-700)',
            whiteSpace: 'pre-wrap',
            fontSize: 'var(--text-body)',
          }}
        >
          {entry.reason}
        </p>
      )}
    </>
  );
}
