import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { SessionContext } from '../api/authApi';
import { SignOutButton } from './SignOutButton';
import { ChangePasswordPanel } from './ChangePasswordPanel';
import { SessionsPanel } from './SessionsPanel';

interface AccountStripProps {
  session: SessionContext;
}

export function AccountStrip({ session }: AccountStripProps): JSX.Element {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <section
      aria-label={t('auth.account.heading')}
      style={{
        width: '100%',
        maxWidth: '900px',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-3)',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--space-3)',
          flexWrap: 'wrap',
        }}
      >
        <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-md)' }}>
          {t('auth.signedIn.as', { email: session.email })}
        </p>

        <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="ui-button ui-button-quiet"
            aria-expanded={open}
            onClick={() => setOpen(!open)}
          >
            {t('auth.account.manage')}
          </button>
          <SignOutButton />
        </div>
      </div>

      {open && (
        <div
          className="ui-card"
          style={{
            padding: 'var(--space-6)',
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-5)',
          }}
        >
          <SessionsPanel />
          <ChangePasswordPanel />
        </div>
      )}
    </section>
  );
}
