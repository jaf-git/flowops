import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { loginMessage } from '../api/authErrors';
import { useLogin } from '../hooks/useAuth';

export function LoginForm(): JSX.Element {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const loginMutation = useLogin();

  function onSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    loginMutation.mutate({ email, password });
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
      <Field id="login-email" label={t('auth.field.email')} required>
        <Input
          id="login-email"
          type="email"
          value={email}
          autoComplete="username"
          required
          onChange={(event) => setEmail(event.target.value)}
        />
      </Field>
      <Field id="login-password" label={t('auth.field.password')} required>
        <Input
          id="login-password"
          type="password"
          value={password}
          autoComplete="current-password"
          required
          onChange={(event) => setPassword(event.target.value)}
        />
      </Field>

      {loginMutation.isError && (
        <p role="alert" className="text-sm text-[var(--alert)]">
          {loginMessage(loginMutation.error, t)}
        </p>
      )}

      <Button
        type="submit"
        loading={loginMutation.isPending}
        loadingLabel={t('auth.login.submitting')}
      >
        {t('auth.login.submit')}
      </Button>
    </form>
  );
}
