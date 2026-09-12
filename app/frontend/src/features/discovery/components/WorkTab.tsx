import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { describeEnding, type ConversationBracket } from '../api/bracketApi';
import { useBracketReach } from '../hooks/useBracketReach';
import { useConversationWork } from '../hooks/useConversationWork';

import { CloseControl } from './CloseControl';
import { HandOverControl, type HandoverCandidate } from './HandOverControl';
import { WaitControl } from './WaitControl';

interface WorkTabProps {
  readonly conversationId: string;

  readonly onOpenMessage?: (messageId: string) => void;

  readonly colleagues?: readonly HandoverCandidate[];

  readonly viewerId?: string;
}

type Act = 'CLOSE' | 'WAIT' | 'HAND_OVER';

type OpenControl = { bracketId: string; which: Act } | null;

export function WorkTab({
  conversationId,
  onOpenMessage,
  colleagues = [],
  viewerId,
}: WorkTabProps): JSX.Element {
  const { t } = useTranslation();
  const work = useConversationWork(conversationId);

  const reach = useBracketReach(conversationId);
  const [open, setOpen] = useState<OpenControl>(null);

  const [oldestFirst, setOldestFirst] = useState(false);

  if (work.isPending) {
    return <p className="fo-work-quiet">{t('discovery.work.loading')}</p>;
  }

  if (work.isError) {
    return <p className="fo-work-quiet">{t('discovery.work.failed')}</p>;
  }

  const brackets = work.data ?? [];

  if (brackets.length === 0) {
    return (
      <div className="fo-work-empty">
        <p>{t('discovery.work.empty.what')}</p>
        <p className="fo-work-quiet">{t('discovery.work.empty.how')}</p>
      </div>
    );
  }

  const ordered = [...brackets].sort((one, other) =>
    oldestFirst
      ? one.openedAt.localeCompare(other.openedAt)
      : other.openedAt.localeCompare(one.openedAt),
  );

  return (
    <>
      <div className="fo-work-sort">
        <label className="fo-mark-line">
          <span className="fo-mark-part">{t('discovery.work.sortLabel')}</span>
          <select
            className="fo-mark-select"
            value={oldestFirst ? 'oldest' : 'newest'}
            onChange={(event) => {
              setOldestFirst(event.target.value === 'oldest');
            }}
          >
            <option value="newest">{t('discovery.work.sortNewest')}</option>
            <option value="oldest">{t('discovery.work.sortOldest')}</option>
          </select>
        </label>
      </div>

      <ul className="fo-work-list">
        {ordered.map((bracket) => (
          <WorkRow
            key={bracket.bracketId}
            bracket={bracket}
            reach={reach.get(bracket.bracketId) ?? []}
            conversationId={conversationId}
            siblings={brackets}
            colleagues={colleagues}
            open={open?.bracketId === bracket.bracketId ? open.which : null}
            onToggle={(which) => {
              setOpen((was) =>
                was?.bracketId === bracket.bracketId && was.which === which
                  ? null
                  : { bracketId: bracket.bracketId, which },
              );
            }}
            onDone={() => {
              setOpen(null);
            }}
            onOpenMessage={onOpenMessage}
            viewerId={viewerId}
          />
        ))}
      </ul>
    </>
  );
}

function WorkRow({
  bracket,
  reach,
  conversationId,
  siblings,
  colleagues,
  open,
  onToggle,
  onDone,
  onOpenMessage,
  viewerId,
}: {
  viewerId?: string;
  bracket: ConversationBracket;

  reach: readonly string[];
  conversationId: string;
  siblings: readonly ConversationBracket[];
  colleagues: readonly HandoverCandidate[];
  open: Act | null;
  onToggle: (which: Act) => void;
  onDone: () => void;
  onOpenMessage?: (messageId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const ending = bracket.closeKind === null ? null : describeEnding(bracket.closeKind);
  const firstMessage = bracket.messageIds[0];

  return (
    <li className="fo-work-row" data-state={bracket.state}>
      <span className="fo-work-address">{bracket.address}</span>

      <span className="fo-work-who">{bracket.performerName ?? t('discovery.work.unclaimed')}</span>

      {ending === null ? (
        <span className="fo-work-state" data-live="true">
          <span aria-hidden="true">{bracket.state === 'WAITING' ? '◇' : '◆'}</span>
          {bracket.state === 'WAITING'
            ? t('discovery.work.waitingOn', { count: bracket.openWaits })
            : t('discovery.work.open')}
        </span>
      ) : (
        <span className="fo-work-state" data-completed={ending.completed}>
          <span aria-hidden="true">{ending.completed ? '✓' : '—'}</span>
          {ending.label}
        </span>
      )}

      {bracket.outputValue === null || bracket.outputKind === 'MESSAGE_REF' ? null : (
        <span className="fo-work-output" title={bracket.outputValue}>
          {bracket.outputValue}
        </span>
      )}

      <span className="fo-work-opened">
        {t('discovery.work.openedWhen')} <RelativeTime value={bracket.openedAt} />
      </span>

      <RelativeTime className="fo-work-moved" value={bracket.lastActivityAt} />

      {bracket.openWaits === 0 ? null : (
        <span className="fo-work-blocked">
          {t('discovery.work.blockedBy', { count: bracket.openWaits })}
        </span>
      )}

      {reach.length === 0 ? null : (
        <span className="fo-work-reach">{t('discovery.work.alsoIn', { count: reach.length })}</span>
      )}

      {firstMessage === undefined || onOpenMessage === undefined ? null : (
        <button
          type="button"
          className="fo-work-open"
          aria-label={t('discovery.work.openMessage', { count: bracket.messageIds.length })}
          title={t('discovery.work.openMessage', { count: bracket.messageIds.length })}
          onClick={() => {
            onOpenMessage(firstMessage);
          }}
        >
          <span aria-hidden="true">↗</span>
          {bracket.messageIds.length > 1 ? (
            <span className="fo-work-count">{bracket.messageIds.length}</span>
          ) : null}
        </button>
      )}

      {!bracket.live ? null : (
        <div className="fo-work-acts">
          {viewerId !== undefined && bracket.performerId !== viewerId ? null : (
            <button
              type="button"
              className="ui-chip"
              aria-expanded={open === 'WAIT'}
              data-selected={open === 'WAIT'}
              onClick={() => {
                onToggle('WAIT');
              }}
            >
              Waiting
            </button>
          )}
          <button
            type="button"
            className="ui-chip"
            aria-expanded={open === 'CLOSE'}
            data-selected={open === 'CLOSE'}
            onClick={() => {
              onToggle('CLOSE');
            }}
          >
            Close
          </button>

          {bracket.performerId === null ? null : (
            <button
              type="button"
              className="ui-chip"
              aria-expanded={open === 'HAND_OVER'}
              data-selected={open === 'HAND_OVER'}
              onClick={() => {
                onToggle('HAND_OVER');
              }}
            >
              Hand over
            </button>
          )}
        </div>
      )}

      {open === 'CLOSE' ? (
        <CloseControl
          conversationId={conversationId}
          bracketId={bracket.bracketId}

          markedText={bracket.outputValue ?? undefined}
          onClosed={onDone}
        />
      ) : null}

      {open === 'WAIT' ? (
        <WaitControl
          conversationId={conversationId}
          bracketId={bracket.bracketId}
          candidates={siblings}
          onDeclared={onDone}
        />
      ) : null}

      {open === 'HAND_OVER' ? (
        <HandOverControl
          conversationId={conversationId}

          bracket={bracket}
          candidates={colleagues}
        />
      ) : null}
    </li>
  );
}
