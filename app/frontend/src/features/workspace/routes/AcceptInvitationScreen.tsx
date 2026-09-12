import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { PasswordRules } from '../../../shared/ui/PasswordRules';
import { PublicPageFrame } from '../../../shared/ui/PublicPageFrame';
import { ScrollRegion } from '../../../shared/ui/ScrollRegion';
import { Spinner } from '../../../shared/ui/Spinner';
import { invitedPasswordRuleMessage, joinRefusalMessage } from '../api/invitationErrors';
import { useInvitation } from '../hooks/useInvitation';
import { useAcceptInvitation, useDeclineInvitation } from '../hooks/useJoinWorkspace';

interface AcceptInvitationScreenProps {
  token: string;
}

export function AcceptInvitationScreen({ token }: AcceptInvitationScreenProps): JSX.Element {
  const { t } = useTranslation();
  const invitation = useInvitation(token);

  if (token === '') {
    return (
      <PublicPageFrame>
        <Unusable />
      </PublicPageFrame>
    );
  }

  if (invitation.isPending) {
    return (
      <PublicPageFrame>
        <Card>
          <Spinner label={t('workspace.invitation.loading')} />
        </Card>
      </PublicPageFrame>
    );
  }

  if (isDeadInvitation(invitation.error)) {
    return (
      <PublicPageFrame>
        <Unusable />
      </PublicPageFrame>
    );
  }

  if (invitation.isError || invitation.data === undefined) {
    return (
      <PublicPageFrame>
        <Unchecked onRetry={() => void invitation.refetch()} />
      </PublicPageFrame>
    );
  }

  const { workspaceName, role, manager, inviter, consent } = invitation.data;

  return (
    <PublicPageFrame>
      <Card pad={28}>
        <div style={{ display: 'grid', gap: 'var(--space-5)' }}>
          <div style={{ display: 'grid', gap: 'var(--space-2)' }}>
            <h2 style={{ margin: 0, fontSize: 'var(--text-xl)' }}>
              {t('workspace.invitation.heading')}
            </h2>
            <p style={{ margin: 0 }}>
              {t('workspace.invitation.intro', {
                inviter: inviter.displayName,
                workspace: workspaceName,
              })}
            </p>
          </div>

          <dl style={{ display: 'grid', gap: 'var(--space-3)', margin: 0 }}>
            <Fact
              label={t('workspace.invitation.role.label')}
              value={t(`workspace.invitation.role.${role}`)}
            />
            <Fact label={t('workspace.invitation.manager.label')} value={manager.displayName} />
          </dl>

          {manager.reassigned ? (
            <Banner tone="waiting">
              {t('workspace.invitation.reassigned', { manager: manager.displayName })}
            </Banner>
          ) : null}

          <div style={{ display: 'grid', gap: 'var(--space-2)' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--text-md)' }}>
              {t('workspace.invitation.consent.heading')}
            </h3>

            <ScrollRegion label={t('workspace.invitation.consent.regionLabel')}>
              {consent.text}
            </ScrollRegion>
            <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
              {t('workspace.invitation.consent.version', { version: consent.version })}
            </p>
          </div>

          <JoinForm token={token} consentVersion={consent.version} />
        </div>
      </Card>
    </PublicPageFrame>
  );
}

function Unusable(): JSX.Element {
  const { t } = useTranslation();

  return (
    <Card>
      <div style={{ display: 'grid', gap: 'var(--space-3)' }}>
        <h2 style={{ margin: 0, fontSize: 'var(--text-lg)' }}>
          {t('workspace.invitation.unusable.heading')}
        </h2>
        <p style={{ margin: 0, color: 'var(--muted)', maxWidth: '46ch' }}>
          {t('workspace.invitation.unusable.body')}
        </p>
      </div>
    </Card>
  );
}

function isDeadInvitation(failure: Error | null): boolean {
  return failure instanceof ApiError && failure.status === 410;
}

function Unchecked({ onRetry }: { onRetry: () => void }): JSX.Element {
  const { t } = useTranslation();

  return (
    <Card>
      <div style={{ display: 'grid', gap: 'var(--space-3)', justifyItems: 'start' }}>
        <h2 style={{ margin: 0, fontSize: 'var(--text-lg)' }}>
          {t('workspace.invitation.unchecked.heading')}
        </h2>
        <p style={{ margin: 0, color: 'var(--muted)', maxWidth: '46ch' }}>
          {t('workspace.invitation.unchecked.body')}
        </p>
        <Button type="button" onClick={onRetry}>
          {t('workspace.invitation.unchecked.retry')}
        </Button>
      </div>
    </Card>
  );
}

function Fact({ label, value }: { label: string; value: string }): JSX.Element {
  return (
    <div style={{ display: 'grid', gap: '2px' }}>
      <dt style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>{label}</dt>
      <dd style={{ margin: 0, fontWeight: 600 }}>{value}</dd>
    </div>
  );
}

function JoinForm({
  token,
  consentVersion,
}: {
  token: string;
  consentVersion: string;
}): JSX.Element {
  const { t, i18n } = useTranslation();
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [agreed, setAgreed] = useState(false);
  const join = useAcceptInvitation(token);
  const decline = useDeclineInvitation(token);

  if (join.isSuccess) {
    window.location.assign(`/${i18n.language}`);
  }

  if (decline.isSuccess) {
    return (
      <Banner tone="info">
        <strong>{t('workspace.invitation.declined.heading')}</strong>{' '}
        {t('workspace.invitation.declined.body')}
      </Banner>
    );
  }

  const refusal = joinRefusalMessage(join.error, t);
  const ruleMissed = invitedPasswordRuleMessage(join.error, t);
  const busy = join.isPending || decline.isPending;

  return (
    <form
      style={{ display: 'grid', gap: 'var(--space-3)' }}
      onSubmit={(event) => {
        event.preventDefault();
        join.mutate({ displayName: name, password, consentAccepted: agreed, consentVersion });
      }}
    >
      {refusal === undefined ? null : (
        <Banner tone="alert">
          <strong>{t('workspace.invitation.join.error.heading')}</strong> {refusal}
        </Banner>
      )}

      <Field
        id="invited-name"
        label={t('workspace.invitation.join.name.label')}
        hint={t('workspace.invitation.join.name.hint')}
        required
      >
        <Input
          id="invited-name"
          value={name}
          autoComplete="name"
          onChange={(event) => setName(event.target.value)}
        />
      </Field>

      <Field
        id="invited-password"
        label={t('workspace.invitation.join.password.label')}
        error={ruleMissed}
        required
      >
        <Input
          id="invited-password"
          type="password"
          value={password}
          autoComplete="new-password"
          invalid={ruleMissed !== undefined}
          describedBy={ruleMissed === undefined ? undefined : 'error'}
          onChange={(event) => setPassword(event.target.value)}
        />
      </Field>
      <PasswordRules password={password} id="invited-password-rules" />

      <Checkbox
        id="invited-consent"
        label={t('workspace.invitation.join.agree')}
        checked={agreed}
        onChange={setAgreed}
      />

      <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
        <Button
          type="submit"
          disabled={!agreed || busy}
          loading={join.isPending}
          loadingLabel={t('workspace.invitation.join.submitting')}
        >
          {t('workspace.invitation.join.submit')}
        </Button>
        <Button type="button" variant="quiet" disabled={busy} onClick={() => decline.mutate()}>
          {t('workspace.invitation.join.decline')}
        </Button>
      </div>
    </form>
  );
}
