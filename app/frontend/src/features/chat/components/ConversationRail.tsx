import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Avatar } from '../../../shared/ui/Avatar';
import type { ConversationRow, JoinableRoom } from '../api/chatApi';

interface ConversationRailProps {
  conversations: readonly ConversationRow[];

  joinable?: readonly JoinableRoom[];
  onJoin?: (conversationId: string) => void;
  joining?: boolean;
  selected: string | undefined;
  onSelect: (id: string) => void;
  loading: boolean;
}

export function ConversationRail({
  conversations,
  joinable = [],
  onJoin,
  joining = false,
  selected,
  onSelect,
  loading,
}: ConversationRailProps): JSX.Element {
  const { t } = useTranslation();

  if (loading) {
    return (
      <div aria-busy="true" aria-label={t('chat.rail.loading')} className="fo-queue-list">
        {[0, 1, 2].map((row) => (
          <div key={row} className="fo-queue-skeleton" />
        ))}
      </div>
    );
  }

  if (conversations.length === 0 && joinable.length === 0) {
    return (
      <p
        style={{
          margin: 0,
          padding: 'var(--space-4) var(--space-3)',
          fontSize: 'var(--text-sm)',
          color: 'var(--muted)',
          lineHeight: 1.6,
        }}
      >
        {t('chat.rail.empty')}
      </p>
    );
  }

  const groups: ReadonlyArray<{ kind: ConversationRow['kind']; label: string }> = [
    { kind: 'ANNOUNCEMENT', label: t('chat.rail.group.announcements') },
    { kind: 'CHANNEL', label: t('chat.rail.group.channels') },
    { kind: 'GROUP', label: t('chat.rail.group.groups') },
    { kind: 'DIRECT', label: t('chat.rail.group.direct') },
  ];

  return (
    <div>
      {groups.map((group) => {
        const rows = conversations.filter((row) => row.kind === group.kind);
        if (rows.length === 0) {
          return null;
        }
        return (
          <section key={group.kind} style={{ marginBottom: 'var(--space-4)' }}>
            <h2 className="fo-eyebrow" style={{ padding: '0 var(--space-3)' }}>
              {group.label}
            </h2>
            <div className="fo-queue-list">
              {rows.map((row) => (
                <ConversationItem
                  key={row.id}
                  row={row}
                  selected={row.id === selected}
                  onSelect={onSelect}
                />
              ))}
            </div>
          </section>
        );
      })}

      {joinable.length > 0 ? (
        <section style={{ marginBottom: 'var(--space-4)' }}>
          <h2 className="fo-eyebrow" style={{ padding: '0 var(--space-3)' }}>
            {t('chat.rail.group.joinable')}
          </h2>
          {joinable.map((room) => (
            <div
              key={room.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 'var(--space-2)',
                padding: 'var(--space-1) var(--space-3)',
              }}
            >
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  fontSize: 'var(--text-md)',
                  color: 'var(--muted)',
                }}
              >
                {room.name}
              </span>
              <button
                type="button"
                className="ui-button ui-button-quiet"
                disabled={joining || onJoin === undefined}
                onClick={() => onJoin?.(room.id)}
              >
                {t('chat.rail.join')}
              </button>
            </div>
          ))}
        </section>
      ) : null}
    </div>
  );
}

function ConversationItem({
  row,
  selected,
  onSelect,
}: {
  row: ConversationRow;
  selected: boolean;
  onSelect: (id: string) => void;
}): JSX.Element {
  const { t } = useTranslation();

  const label =
    row.kind === 'ANNOUNCEMENT'
      ? t('chat.rail.announcements')
      : row.kind === 'DIRECT'
        ? (row.counterpartName ?? t('chat.rail.formerMember'))
        : (row.name ?? t('chat.rail.unnamedRoom'));

  const gone = row.kind === 'DIRECT' && !row.counterpartActive;

  return (
    <button
      type="button"
      className="fo-queue-card"
      onClick={() => onSelect(row.id)}
      aria-current={selected ? 'true' : undefined}
    >
      <span className="fo-queue-head">
        {row.kind === 'DIRECT' ? (
          <Avatar id={row.counterpartId ?? row.id} name={row.counterpartName ?? ''} size={34} />
        ) : (
          <RoomGlyph kind={row.kind} />
        )}
        <span className="fo-queue-ident">
          <span className="fo-queue-eyebrow">{t(`chat.header.kind.${row.kind}`)}</span>
          <span className="fo-queue-title">{label}</span>
        </span>

        <span className="fo-queue-open" aria-hidden="true">
          ↗
        </span>
      </span>

      <span className="fo-queue-row">
        <span className="fo-queue-glyph" aria-hidden="true">
          ✉
        </span>
        <span className="fo-queue-line">
          {row.lastMessageDeleted
            ? t('chat.message.deleted')
            : (row.lastMessagePreview ?? t('chat.rail.noMessagesYet'))}
        </span>
        {gone ? (
          <span className="fo-queue-pill">
            <span aria-hidden="true">—</span>
            <span>{t('chat.rail.deactivated')}</span>
          </span>
        ) : null}
        {row.unreadCount > 0 ? (
          <span
            className="fo-queue-count"
            aria-label={t('chat.rail.unread', { count: row.unreadCount })}
          >
            {row.unreadCount}
          </span>
        ) : null}
      </span>
    </button>
  );
}

export function RoomGlyph({ kind }: { kind: ConversationRow['kind'] }): JSX.Element {
  const glyph = kind === 'CHANNEL' ? '#' : kind === 'GROUP' ? '◇' : '!';

  return (
    <span
      aria-hidden="true"
      style={{
        flexShrink: 0,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',

        width: '34px',
        height: '34px',
        borderRadius: 'var(--radius-sm)',

        background: 'var(--surface-inset)',
        color: 'var(--ink-500)',
        fontSize: 'var(--text-md)',

        fontWeight: 600,
        lineHeight: 1,
      }}
    >
      {glyph}
    </span>
  );
}
