import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { SessionSummary } from '../api/authApi';

interface SessionListProps {
  sessions: SessionSummary[];
  onTerminate?: (reference: string) => void;
  terminatingReference?: string;
}

export function SessionList({
  sessions,
  onTerminate,
  terminatingReference,
}: SessionListProps): JSX.Element {
  const { t, i18n } = useTranslation();

  if (sessions.length === 0) {
    return <p className="text-sm text-[var(--muted)]">{t('auth.sessions.none')}</p>;
  }

  return (
    <ul className="flex flex-col gap-3">
      {sessions.map((session) => (
        <li
          key={session.reference}
          className="flex flex-col gap-2 rounded-md border border-[var(--line-soft)] p-4 text-sm sm:flex-row sm:items-start sm:justify-between"
        >
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <span className="font-medium text-[var(--ink)]">{session.deviceSummary}</span>
              {session.current && (
                <span className="rounded-full bg-[var(--done-soft)] px-2 py-0.5 text-xs font-medium text-[var(--done)]">
                  {t('auth.sessions.current')}
                </span>
              )}
            </div>
            <span className="text-[var(--slate)]">
              {t('auth.sessions.whereFrom', {
                ipAddress: session.ipAddress,
                coarseLocation: session.coarseLocation,
              })}
            </span>
            <span className="text-[var(--muted)]">
              {t('auth.sessions.lastActive', {
                at: formatInstant(session.lastActiveAt, i18n.language),
              })}
            </span>
            <span className="text-[var(--muted)]">
              {t('auth.sessions.signedInAt', {
                at: formatInstant(session.createdAt, i18n.language),
              })}
            </span>
          </div>

          {onTerminate !== undefined && !session.current && (
            <button
              type="button"
              onClick={() => onTerminate(session.reference)}
              disabled={terminatingReference === session.reference}
              className="self-start rounded-md border border-[var(--alert-line)] px-3 py-1.5 font-medium text-[var(--alert)] disabled:opacity-60"
            >
              {terminatingReference === session.reference
                ? t('auth.sessions.terminating')
                : t('auth.sessions.terminate')}
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

function formatInstant(value: string, locale: string): string {
  const instant = new Date(value);
  return Number.isNaN(instant.getTime()) ? value : instant.toLocaleString(locale);
}
