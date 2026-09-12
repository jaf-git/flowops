import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import type { ConversationWait } from '../api/bracketApi';
import { useConversationWaits, useEndWait } from '../hooks/useConversationWork';

interface WaitingTabProps {
  readonly conversationId: string;
}

export function WaitingTab({ conversationId }: WaitingTabProps): JSX.Element {
  const { t } = useTranslation();
  const waits = useConversationWaits(conversationId);

  if (waits.isPending) {
    return <p className="fo-work-quiet">{t('discovery.waiting.loading')}</p>;
  }

  if (waits.isError) {
    return <p className="fo-work-quiet">{t('discovery.waiting.failed')}</p>;
  }

  const rows = waits.data ?? [];

  if (rows.length === 0) {
    return (
      <div className="fo-work-empty">
        <p>{t('discovery.waiting.empty.what')}</p>
        <p className="fo-work-quiet">{t('discovery.waiting.empty.how')}</p>
      </div>
    );
  }

  return (
    <ul className="fo-work-list">
      {rows.map((wait) => (
        <WaitRow key={wait.waitId} wait={wait} conversationId={conversationId} />
      ))}
    </ul>
  );
}

function WaitRow({
  wait,
  conversationId,
}: {
  wait: ConversationWait;
  conversationId: string;
}): JSX.Element {
  const { t } = useTranslation();
  const end = useEndWait(conversationId);

  return (
    <li className="fo-work-row" data-external={wait.external}>
      <span className="fo-wait-kind" data-external={wait.external}>
        <span aria-hidden="true">{wait.external ? '◇' : '◆'}</span>
        {t(`discovery.waiting.kind.${wait.kind}`)}
      </span>

      <span className="fo-wait-reason">{wait.reason ?? t('discovery.waiting.noReason')}</span>

      {wait.blockingAddress === null ? null : (
        <span className="fo-wait-on">{wait.blockingAddress}</span>
      )}

      {wait.expectedBy === null ? null : (
        <time className="fo-wait-when" dateTime={wait.expectedBy}>
          {t('discovery.waiting.expected', {
            date: new Date(wait.expectedBy).toLocaleDateString(),
          })}
        </time>
      )}

      <div className="fo-wait-acts">
        {wait.blockingAddress === null ? (
          <button
            type="button"
            className="ui-chip"
            disabled={end.isPending}
            onClick={() => {
              end.mutate({ waitId: wait.waitId, how: 'arrived' });
            }}
          >
            {t('discovery.waiting.arrived')}
          </button>
        ) : null}

        <button
          type="button"
          className="ui-chip"
          disabled={end.isPending}
          onClick={() => {
            end.mutate({ waitId: wait.waitId, how: 'withdraw' });
          }}
        >
          {t('discovery.waiting.withdraw')}
        </button>
      </div>

      {end.isError ? (
        <p className="fo-close-failed">
          {end.error instanceof ApiError ? end.error.message : t('discovery.waiting.endFailed')}
        </p>
      ) : null}
    </li>
  );
}
