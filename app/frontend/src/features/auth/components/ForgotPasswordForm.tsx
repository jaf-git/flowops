import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { useRequestPasswordReset } from '../hooks/useAuth';

export function ForgotPasswordForm({ onGiveUp }: { onGiveUp: () => void }): JSX.Element {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const requestReset = useRequestPasswordReset();

  function onSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    requestReset.mutate(email);
  }

  if (requestReset.isSuccess) {
    return (
      <section className="flex flex-col gap-5">
        <p className="text-sm text-[var(--slate)]">{t('auth.forgotPassword.sent', { email })}</p>
        <Button type="button" variant="quiet" onClick={onGiveUp}>
          {t('auth.forgotPassword.backToLogin')}
        </Button>
      </section>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
      <p className="text-sm text-[var(--slate)]">{t('auth.forgotPassword.explain')}</p>

      <Field id="forgot-email" label={t('auth.field.email')} required>
        <Input
          id="forgot-email"
          type="email"
          value={email}
          autoComplete="username"
          required
          onChange={(event) => setEmail(event.target.value)}
        />
      </Field>

      {requestReset.isError && (
        <p role="alert" className="text-sm text-[var(--alert)]">
          {t('auth.error.unexpected')}
        </p>
      )}

      <Button
        type="submit"
        loading={requestReset.isPending}
        loadingLabel={t('auth.forgotPassword.sending')}
      >
        {t('auth.forgotPassword.submit')}
      </Button>

      <Button type="button" variant="quiet" onClick={onGiveUp}>
        {t('auth.forgotPassword.backToLogin')}
      </Button>
    </form>
  );
}
