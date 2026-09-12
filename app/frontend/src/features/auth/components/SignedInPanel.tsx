import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Card } from '../../../shared/ui/Card';
import type { SessionContext } from '../api/authApi';
import { SignOutButton } from './SignOutButton';
import { ChangePasswordPanel } from './ChangePasswordPanel';
import { SessionAdministrationPanel } from './SessionAdministrationPanel';
import { SessionsPanel } from './SessionsPanel';

interface AccountPanelProps {
  session: SessionContext;
}

export function SignedInPanel({ session }: AccountPanelProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <div
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)', maxWidth: '760px' }}
    >
      <Card>
        <h2 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 700 }}>
          {t('auth.account.heading')}
        </h2>
        <p
          style={{
            margin: 'var(--space-2) 0 0',
            color: 'var(--muted)',
            fontSize: 'var(--text-base)',
          }}
        >
          {t('auth.signedIn.as', { email: session.email })}
        </p>
      </Card>

      <Card>
        <SessionsPanel />
      </Card>

      {session.permissions.includes('SESSION_VIEW_ANY') && (
        <Card>
          <SessionAdministrationPanel />
        </Card>
      )}

      <Card>
        <ChangePasswordPanel />
      </Card>

      <SignOutButton style={{ alignSelf: 'flex-start' }} />
    </div>
  );
}
