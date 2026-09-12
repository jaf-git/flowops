import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Chip } from '../ui/Chip';
import type { ConnectionStatus } from './RealtimeClient';

const TONE: Record<ConnectionStatus, 'neutral' | 'done' | 'waiting' | 'blocked'> = {
  connecting: 'neutral',
  live: 'done',
  reconnecting: 'waiting',
  offline: 'blocked',
};

function Glyph({ status }: { status: ConnectionStatus }): JSX.Element {
  return (
    <svg
      data-glyph={status}
      aria-hidden="true"
      width="10"
      height="10"
      viewBox="0 0 10 10"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      style={{ flexShrink: 0 }}
    >
      {status === 'live' && <circle cx="5" cy="5" r="3" fill="currentColor" stroke="none" />}
      {status === 'connecting' && <circle cx="5" cy="5" r="3" strokeDasharray="2 2" />}
      {status === 'reconnecting' && (
        <>
          <path d="M8 5a3 3 0 1 1-1.1-2.3" />
          <path d="M8.2 1.2v1.6H6.6" />
        </>
      )}
      {status === 'offline' && (
        <>
          <circle cx="5" cy="5" r="3.2" />
          <path d="M2.7 7.3 7.3 2.7" />
        </>
      )}
    </svg>
  );
}

interface ConnectionIndicatorProps {
  status: ConnectionStatus;
}

export function ConnectionIndicator({ status }: ConnectionIndicatorProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <span role="status" aria-live="polite">
      <Chip tone={TONE[status]}>
        <Glyph status={status} />
        <span className="sr-only">{t('ui.connection.label')}: </span>
        {t(`ui.connection.${status}`)}
      </Chip>
    </span>
  );
}
