import { useState, type CSSProperties, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { useInbox, useMarkRead } from '../hooks/useNotifications';
import {
  inboxOrder,
  isDigest,
  sentenceKeyFor,
  subjectOf,
  type NotificationRow,
  type NotificationSubject,
} from '../model/notification';
import { NotificationPreferences } from './NotificationPreferences';

const ROW: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'baseline',
  gap: 'var(--space-2)',
  width: '100%',
  minWidth: 0,
  padding: 'var(--space-3) 0',
  border: 'none',
  borderBlockEnd: '1px solid var(--line)',
  background: 'transparent',
  textAlign: 'start',
  font: 'inherit',
  fontSize: 'var(--text-sm)',
  color: 'var(--ink)',
  overflowWrap: 'anywhere',
};

const WHEN: CSSProperties = {
  marginInlineStart: 'auto',
  flexShrink: 0,
  fontSize: 'var(--text-xs)',
  color: 'var(--muted)',
};

interface NotificationInboxProps {
  onOpenSubject: (subject: NotificationSubject) => void;
}

export function NotificationInbox({ onOpenSubject }: NotificationInboxProps): JSX.Element {
  const { t } = useTranslation();
  const inbox = useInbox(true);
  const markRead = useMarkRead();

  const rows = inboxOrder(inbox.data?.items ?? []);

  function open(row: NotificationRow): void {
    const subject = subjectOf(row);
    if (subject === undefined) {
      return;
    }

    markRead.mutate(row.id);
    onOpenSubject(subject);
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        minWidth: 0,
        padding: 'var(--space-4)',
      }}
    >
      <div className="fo-notif-list" style={{ minWidth: 0 }}>
        {rows.length === 0 ? (
          <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--muted)' }}>
            {t('notification.inbox.empty')}
          </p>
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, minWidth: 0 }}>
            {rows.map((row) => (
              <li key={row.id} style={{ minWidth: 0 }}>
                {isDigest(row) ? (
                  <Digest row={row} onOpenSubject={open} onRead={(id) => markRead.mutate(id)} />
                ) : (
                  <Line row={row} onOpen={open} />
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <NotificationPreferences />
    </div>
  );
}

function Line({
  row,
  onOpen,
}: {
  row: NotificationRow;
  onOpen: (row: NotificationRow) => void;
}): JSX.Element {
  const { t } = useTranslation();

  const sentence = t(sentenceKeyFor(row.kind));
  const dimmed: CSSProperties = row.readAt === null ? {} : { opacity: 0.62 };

  if (row.subjectId === null) {
    return (
      <div style={{ ...ROW, ...dimmed }}>
        <span style={{ minWidth: 0 }}>{sentence}</span>
        <span style={{ minWidth: 0, color: 'var(--muted)' }}>{t('notification.subject.gone')}</span>
        <span style={WHEN}>
          <RelativeTime value={row.createdAt} />
        </span>
      </div>
    );
  }

  return (
    <button
      type="button"
      style={{ ...ROW, ...dimmed, cursor: 'pointer' }}
      onClick={() => onOpen(row)}
    >
      <span style={{ minWidth: 0 }}>{sentence}</span>
      <span style={WHEN}>
        <RelativeTime value={row.createdAt} />
      </span>
    </button>
  );
}

function Digest({
  row,
  onOpenSubject,
  onRead,
}: {
  row: NotificationRow;
  onOpenSubject: (row: NotificationRow) => void;
  onRead: (id: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);

  function toggle(): void {
    if (!expanded && row.readAt === null) {
      onRead(row.id);
    }
    setExpanded(!expanded);
  }

  return (
    <>
      <button
        type="button"
        aria-expanded={expanded}
        style={{ ...ROW, ...(row.readAt === null ? {} : { opacity: 0.62 }), cursor: 'pointer' }}
        onClick={toggle}
      >
        <span style={{ minWidth: 0 }}>
          {t('notification.digest.summary', { count: row.items.length })}
        </span>
        <span style={WHEN}>
          <RelativeTime value={row.createdAt} />
        </span>
      </button>

      {expanded ? (
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            minWidth: 0,
            paddingInlineStart: 'var(--space-4)',
          }}
        >
          {inboxOrder(row.items).map((item) => (
            <li key={item.id} style={{ minWidth: 0 }}>
              <Line row={item} onOpen={onOpenSubject} />
            </li>
          ))}
        </ul>
      ) : null}
    </>
  );
}
