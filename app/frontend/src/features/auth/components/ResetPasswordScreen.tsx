import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { passwordRuleMessage } from '../../../shared/api/passwordRuleMessage';
import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { PasswordRules } from '../../../shared/ui/PasswordRules';
import { PublicPageFrame } from '../../../shared/ui/PublicPageFrame';
import { PASSWORD_RULES } from '../../../shared/ui/passwordPolicy';
import { Spinner } from '../../../shared/ui/Spinner';
import { useCompletePasswordReset, useResetToken } from '../hooks/useAuth';

export function ResetPasswordScreen({ token }: { token: string }): JSX.Element {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const link = useResetToken(token);
  const completeReset = useCompletePasswordReset();

  function onSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    completeReset.mutate({ token, newPassword: password });
  }

  if (completeReset.isSuccess) {
    return (
      <PublicPageFrame heading={t('auth.resetPassword.doneHeading')}>
        <p className="text-sm text-[var(--slate)]" style={{ maxWidth: '46ch' }}>
          {t('auth.resetPassword.done')}
        </p>
        <a className="ui-button ui-button-primary" href="/">
          {t('auth.resetPassword.signIn')}
        </a>
      </PublicPageFrame>
    );
  }

  if (token === '' || isDeadLink(link.error)) {
    return (
      <PublicPageFrame heading={t('auth.resetPassword.deadHeading')}>
        <p className="text-sm text-[var(--slate)]" style={{ maxWidth: '46ch' }}>
          {t('auth.resetPassword.dead')}
        </p>
        <a className="ui-button ui-button-primary" href="/">
          {t('auth.resetPassword.askAgain')}
        </a>
      </PublicPageFrame>
    );
  }

  if (link.isError) {
    return (
      <PublicPageFrame heading={t('auth.resetPassword.uncheckedHeading')}>
        <p className="text-sm text-[var(--slate)]" style={{ maxWidth: '46ch' }}>
          {t('auth.resetPassword.unchecked')}
        </p>
        <Button type="button" onClick={() => void link.refetch()}>
          {t('auth.resetPassword.retry')}
        </Button>
      </PublicPageFrame>
    );
  }

  if (link.isPending) {
    return (
      <PublicPageFrame heading={t('auth.resetPassword.heading')}>
        <Spinner label={t('auth.resetPassword.checking')} />
      </PublicPageFrame>
    );
  }

  const refusedRule = shownOnlyInARefusal(completeReset.error)
    ? passwordRuleMessage(completeReset.error, t)
    : undefined;

  return (
    <PublicPageFrame heading={t('auth.resetPassword.heading')}>
      <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
        <p className="text-sm text-[var(--slate)]" style={{ maxWidth: '46ch' }}>
          {t('auth.resetPassword.explain')}
        </p>

        <Field
          id="reset-password"
          label={t('auth.field.password')}
          hint={t('auth.field.passwordHint')}
          error={refusedRule}
          required
        >
          <Input
            id="reset-password"
            type="password"
            value={password}
            autoComplete="new-password"
            required
            onChange={(event) => setPassword(event.target.value)}
          />
        </Field>

        <PasswordRules password={password} id="reset-password-rules" />

        {completeReset.isError && refusedRule === undefined && (
          <p role="alert" className="text-sm text-[var(--alert)]">
            {isDeadLink(completeReset.error)
              ? t('auth.resetPassword.dead')
              : t('auth.resetPassword.saveFailed')}
          </p>
        )}

        <Button
          type="submit"
          loading={completeReset.isPending}
          loadingLabel={t('auth.resetPassword.saving')}
        >
          {t('auth.resetPassword.submit')}
        </Button>
      </form>
    </PublicPageFrame>
  );
}

function isDeadLink(failure: Error | null): boolean {
  return failure instanceof ApiError && failure.status === 410;
}

function shownOnlyInARefusal(failure: Error | null): boolean {
  if (!(failure instanceof ApiError) || failure.code !== 'PASSWORD_POLICY_VIOLATION') {
    return false;
  }
  const rule = failure.details.find((violation) => violation.field === 'password')?.rule;
  return rule !== undefined && !(PASSWORD_RULES as readonly string[]).includes(rule);
}
