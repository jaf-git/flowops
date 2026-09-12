import { useEffect, type JSX, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { SetupProgress } from '../components/SetupProgress';
import { WorkspaceSetupForm } from '../components/WorkspaceSetupForm';
import { useSetupDraft } from '../hooks/useSetupDraft';
import { useSetupPrefill } from '../hooks/useWorkspaceSetup';
import type { SetupPrefill } from '../api/workspaceApi';

interface WorkspaceSetupScreenProps {
  accountStrip: ReactNode;
}

export function WorkspaceSetupScreen({ accountStrip }: WorkspaceSetupScreenProps): JSX.Element {
  const { t } = useTranslation();
  const prefill = useSetupPrefill();

  if (prefill.isPending) {
    return (
      <SetupPage accountStrip={accountStrip}>
        <p style={{ margin: 0, padding: 'var(--space-8) var(--space-7)', color: 'var(--muted)' }}>
          {t('workspace.setup.loading')}
        </p>
      </SetupPage>
    );
  }

  if (prefill.isError || prefill.data === undefined) {
    return (
      <SetupPage accountStrip={accountStrip}>
        <p
          role="alert"
          style={{ margin: 0, padding: 'var(--space-8) var(--space-7)', color: 'var(--alert)' }}
        >
          {t('workspace.setup.unavailable')}
        </p>
      </SetupPage>
    );
  }

  if (prefill.data.setupCompleted) {
    return <AlreadySetUp />;
  }

  return (
    <SetupPage accountStrip={accountStrip}>
      <SetupSplit prefill={prefill.data} />
    </SetupPage>
  );
}

function AlreadySetUp(): JSX.Element {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  useEffect(() => {
    void queryClient.invalidateQueries({ queryKey: ['auth', 'session'] });
  }, [queryClient]);

  return (
    <main
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 'var(--space-8) var(--space-6)',
      }}
    >
      <p style={{ margin: 0, color: 'var(--muted)' }}>{t('workspace.setup.alreadyDone')}</p>
    </main>
  );
}

function SetupPage({
  children,
  accountStrip,
}: {
  children: JSX.Element;
  accountStrip: ReactNode;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <main
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 'var(--space-5)',
        padding: 'var(--space-8) var(--space-6)',
      }}
    >
      <h1
        style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 700, letterSpacing: '-0.01em' }}
      >
        {t('app.name')}
      </h1>

      <div
        className="ui-card fo-split"
        style={{
          width: '100%',
          maxWidth: '900px',
          overflow: 'hidden',
          boxShadow: 'var(--shadow-lg)',
        }}
      >
        {children}
      </div>

      {accountStrip}
    </main>
  );
}

function SetupSplit({ prefill }: { prefill: SetupPrefill }): JSX.Element {
  const handle = useSetupDraft(prefill);

  return (
    <>
      <div
        style={{
          padding: 'var(--space-8) var(--space-7)',
          display: 'flex',
          justifyContent: 'center',
        }}
      >
        <WorkspaceSetupForm prefill={prefill} handle={handle} />
      </div>
      <SetupProgress draft={handle.draft} />
    </>
  );
}
