import { useEffect, useRef, useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { Avatar } from '../../../shared/ui/Avatar';
import type { MessageRow, SpokenRow, WorkMarkRow } from '../api/chatApi';
import { whyNotMarkable } from '../model/marking';
import { whyNotSelectable } from '../model/selection';

interface MessageThreadProps {
  messages: readonly MessageRow[];
  loading: boolean;

  onSend: ((body: string) => void) | undefined;
  sending: boolean;
  failed: boolean;

  onOpenTask: (taskId: string) => void;

  onOpenRun: (instanceId: string) => void;

  chosen?: readonly string[];
  onToggleChosen?: (messageId: string) => void;

  workSuggestion?: ReactNode;

  markFor?: (messageId: string, authorId: string) => ReactNode;

  focusMessageId?: string;

  onFocusUsed?: () => void;
}

export function MessageThread({
  messages,
  loading,
  onSend,
  sending,
  failed,
  onOpenTask,
  onOpenRun,
  chosen,
  onToggleChosen,
  workSuggestion,
  markFor,
  focusMessageId,
  onFocusUsed,
}: MessageThreadProps): JSX.Element {
  const { t } = useTranslation();
  const [draft, setDraft] = useState('');
  const bottom = useRef<HTMLDivElement>(null);
  const focused = useRef<HTMLDivElement>(null);

  const [marked, setMarked] = useState<string | undefined>(undefined);

  if (focusMessageId !== undefined && focusMessageId !== marked) {
    setMarked(focusMessageId);
  }

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' });
  }, [messages.length]);

  useEffect(() => {
    if (focusMessageId === undefined || focused.current === null) {
      return;
    }
    focused.current.scrollIntoView({ block: 'center' });
    onFocusUsed?.();

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusMessageId, messages.length]);

  function submit(): void {
    const body = draft.trim();
    if (body === '' || onSend === undefined) {
      return;
    }
    onSend(body);

    setDraft('');
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 }}>
      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',

          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-2)',
          padding: 'var(--space-5) var(--space-6)',
          background: 'var(--bg)',
        }}
      >
        {loading ? (
          <p style={{ color: 'var(--muted)' }}>{t('chat.thread.loading')}</p>
        ) : messages.length === 0 ? (
          <p style={{ color: 'var(--muted)' }}>{t('chat.thread.empty')}</p>
        ) : (
          [...messages].reverse().map((message, index, ordered) => (
            <div key={message.id} ref={message.id === focusMessageId ? focused : undefined}>
              {index === 0 ||
              !sameDay(message.sentAt, (ordered[index - 1] as MessageRow).sentAt) ? (
                <DayDivider at={message.sentAt} />
              ) : null}

              {message.kind === 'WORK_MARK' ? (
                <WorkMark mark={message} onOpenTask={onOpenTask} onOpenRun={onOpenRun} />
              ) : (
                <Message
                  message={message}
                  onOpenTask={onOpenTask}
                  chosen={chosen === undefined ? undefined : chosen.includes(message.id)}
                  onToggleChosen={onToggleChosen}
                  markFor={markFor}
                  focused={message.id === marked}
                />
              )}
            </div>
          ))
        )}
        <div ref={bottom} />
      </div>

      {workSuggestion}

      {onSend === undefined ? (
        <p
          style={{
            margin: 0,
            padding: 'var(--space-4) var(--space-6)',
            borderBlockStart: '1px solid var(--line)',
            background: 'var(--surface)',
            color: 'var(--muted)',
            fontSize: 'var(--text-sm)',
          }}
        >
          {t('chat.thread.readOnly')}
        </p>
      ) : (
        <div
          style={{
            padding: 'var(--space-3) var(--space-6)',
            borderBlockStart: '1px solid var(--line)',
            background: 'var(--surface)',
          }}
        >
          {failed ? (
            <p
              role="alert"
              style={{
                margin: '0 0 var(--space-2)',
                color: 'var(--alert)',
                fontSize: 'var(--text-sm)',
              }}
            >
              {t('chat.thread.sendFailed')}
            </p>
          ) : null}

          <div className="fo-composer">
            <input
              className="fo-composer-field"
              aria-label={t('chat.thread.composerLabel')}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  submit();
                }
              }}
              placeholder={t('chat.thread.placeholder')}
            />
            <div className="fo-composer-actions">
              <button
                type="button"
                className="fo-composer-send"
                onClick={submit}
                disabled={sending || draft.trim() === ''}
              >
                {t('chat.thread.send')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function WorkMark({
  mark,
  onOpenTask,
  onOpenRun,
}: {
  mark: WorkMarkRow;
  onOpenTask: (taskId: string) => void;
  onOpenRun: (instanceId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const actor = mark.authorName ?? t('chat.rail.formerMember');

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-2)',
        margin: 'var(--space-3) 0',
        paddingInline: 'var(--space-2)',
        color: 'var(--faint)',
        fontSize: 'var(--text-xs)',
      }}
    >
      <span aria-hidden="true" style={{ flex: 1, height: 1, background: 'var(--line)' }} />
      <span>
        {t(mark.work.kind === 'TASK' ? 'chat.workMark.gaveTask' : 'chat.workMark.startedRun', {
          actor,
        })}
      </span>

      <button
        type="button"
        className="ui-button ui-button-quiet"
        onClick={() =>
          mark.work.kind === 'TASK' ? onOpenTask(mark.work.id) : onOpenRun(mark.work.id)
        }
        style={{ fontSize: 'var(--text-xs)', padding: '0 var(--space-2)' }}
      >
        {t('chat.workMark.open')}
      </button>
      <time dateTime={mark.sentAt}>{new Date(mark.sentAt).toLocaleTimeString()}</time>
      <span aria-hidden="true" style={{ flex: 1, height: 1, background: 'var(--line)' }} />
    </div>
  );
}

function Message({
  message,
  onOpenTask,
  chosen,
  onToggleChosen,
  markFor,
  focused,
}: {
  message: SpokenRow;
  onOpenTask: (taskId: string) => void;

  chosen?: boolean;
  onToggleChosen?: (messageId: string) => void;

  markFor?: (messageId: string, authorId: string) => ReactNode;

  focused?: boolean;
}): JSX.Element {
  const { t } = useTranslation();
  const author = message.authorName ?? t('chat.rail.formerMember');
  const selecting = chosen !== undefined && onToggleChosen !== undefined;

  const refusedBecause = whyNotSelectable(message);

  const notMarkableBecause = whyNotMarkable(message);

  return (
    <article
      className={
        focused === true
          ? 'fo-message fo-message-card fo-message--focused'
          : 'fo-message fo-message-card'
      }
    >
      {selecting ? (
        refusedBecause === null ? (
          <input
            type="checkbox"
            checked={chosen}
            onChange={() => onToggleChosen(message.id)}
            aria-label={t('chat.selection.choose', { text: message.body.slice(0, 40) })}
            style={{ marginTop: 6 }}
          />
        ) : (
          <span
            style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)', width: 13, marginTop: 4 }}
            title={t(`chat.selection.cannot.${refusedBecause}`)}
            aria-label={t(`chat.selection.cannot.${refusedBecause}`)}
          >
            —
          </span>
        )
      ) : null}

      <Avatar id={message.authorId} name={message.authorName ?? ''} size={28} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <header style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-2)' }}>
          <span style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink)' }}>
            {author}
          </span>
          <time
            dateTime={message.sentAt}
            style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)' }}
          >
            {new Date(message.sentAt).toLocaleTimeString()}
          </time>
          {message.editedAt !== null && message.deletedAt === null ? (
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)' }}>
              {t('chat.message.edited')}
            </span>
          ) : null}

          {focused === true ? (
            <span className="fo-message__came-for">{t('chat.message.cameFor')}</span>
          ) : null}
        </header>
        <p
          style={{
            margin: 'var(--space-1) 0 0',
            color: message.deletedAt === null ? 'var(--slate)' : 'var(--faint)',
            fontStyle: message.deletedAt === null ? 'normal' : 'italic',
            lineHeight: 1.6,
            whiteSpace: 'pre-wrap',
          }}
        >
          {message.deletedAt === null ? message.body : t('chat.message.deleted')}
        </p>

        {message.convertedTaskId !== null ? (
          <button
            type="button"
            onClick={() => onOpenTask(message.convertedTaskId as string)}
            style={{
              display: 'inline-block',
              marginTop: 'var(--space-2)',
              padding: '2px var(--space-2)',
              border: 'none',
              cursor: 'pointer',
              borderRadius: 'var(--radius-chip)',
              background: 'var(--brand-soft)',
              color: 'var(--brand-dark)',

              fontFamily: 'inherit',
              fontSize: 'var(--text-sm)',
              fontWeight: 500,
            }}
          >
            {t('chat.message.becameTask')}
          </button>
        ) : null}
      </div>

      {markFor === undefined ? null : notMarkableBecause === null ? (
        markFor(message.id, message.authorId)
      ) : (
        <span
          style={{
            display: 'inline-block',
            fontSize: 'var(--text-xs)',
            color: 'var(--faint)',
          }}
          title={t(CANNOT_MARK[notMarkableBecause])}
          aria-label={t(CANNOT_MARK[notMarkableBecause])}
        >
          —
        </span>
      )}
    </article>
  );
}

const CANNOT_MARK = {
  ALREADY_MARKED: 'discovery.circle.marked',
  WITHDRAWN: 'chat.selection.cannot.WITHDRAWN',
  NOT_SPOKEN: 'chat.selection.cannot.NOT_SPOKEN',
} as const;

function DayDivider({ at }: { at: string }): JSX.Element {
  const day = new Date(at);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);

  const { t, i18n } = useTranslation();
  const label = sameDay(at, today.toISOString())
    ? t('chat.thread.today')
    : sameDay(at, yesterday.toISOString())
      ? t('chat.thread.yesterday')
      : day.toLocaleDateString(i18n.language, { day: 'numeric', month: 'long' });

  return <div className="fo-day-divider">{label}</div>;
}

function sameDay(left: string, right: string): boolean {
  const a = new Date(left);
  const b = new Date(right);
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}
