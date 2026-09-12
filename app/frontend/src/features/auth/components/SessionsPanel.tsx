import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useOwnSessions } from '../hooks/useAuth';
import { SessionList } from './SessionList';

export function SessionsPanel(): JSX.Element {
  const { t } = useTranslation();
  const sessions = useOwnSessions();

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold text-[var(--ink)]">{t('auth.sessions.heading')}</h2>
        <p className="text-[var(--slate)]">{t('auth.sessions.explanation')}</p>
      </div>

      {sessions.isPending && (
        <p className="text-sm text-[var(--muted)]">{t('auth.sessions.loading')}</p>
      )}

      {sessions.isError && (
        <p
          role="alert"
          className="rounded-md bg-[var(--alert-soft)] p-3 text-sm text-[var(--alert)]"
        >
          {t('auth.error.unexpected')}
        </p>
      )}

      {sessions.data !== undefined && <SessionList sessions={sessions.data} />}
    </section>
  );
}
