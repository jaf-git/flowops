import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { passcodeRequestMessage, passwordRuleMessage, signupMessage } from '../api/authErrors';
import { useCompleteSignup, useRequestSignupPasscode } from '../hooks/useAuth';

type Stage = 'address' | 'passcode';

export function SignupForm(): JSX.Element {
  const { t } = useTranslation();
  const [stage, setStage] = useState<Stage>('address');
  const [email, setEmail] = useState('');
  const [passcode, setPasscode] = useState('');
  const [password, setPassword] = useState('');

  const requestPasscode = useRequestSignupPasscode();
  const completeSignup = useCompleteSignup();

  function onRequestPasscode(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    requestPasscode.mutate(email, { onSuccess: () => setStage('passcode') });
  }

  function onCompleteSignup(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    completeSignup.mutate({ email, passcode, password });
  }

  if (stage === 'address') {
    return (
      <form onSubmit={onRequestPasscode} className="flex flex-col gap-5" noValidate>
        <Field id="signup-email" label={t('auth.field.email')} required>
          <Input
            id="signup-email"
            type="email"
            value={email}
            autoComplete="username"
            required
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>

        {requestPasscode.isError && (
          <p role="alert" className="text-sm text-[var(--alert)]">
            {passcodeRequestMessage(requestPasscode.error, t)}
          </p>
        )}

        <Button
          type="submit"
          loading={requestPasscode.isPending}
          loadingLabel={t('auth.signup.sending')}
        >
          {t('auth.signup.requestCode')}
        </Button>
      </form>
    );
  }

  const passwordRule = passwordRuleMessage(completeSignup.error, t);

  return (
    <form onSubmit={onCompleteSignup} className="flex flex-col gap-5" noValidate>
      <p className="text-sm text-[var(--slate)]">{t('auth.signup.codeSent', { email })}</p>

      <Field id="signup-passcode" label={t('auth.field.passcode')} required>
        <Input
          id="signup-passcode"
          type="text"
          value={passcode}
          autoComplete="one-time-code"
          required
          onChange={(event) => setPasscode(event.target.value)}
        />
      </Field>
      <Field
        id="signup-password"
        label={t('auth.field.password')}
        hint={t('auth.field.passwordHint')}
        error={passwordRule}
        required
      >
        <Input
          id="signup-password"
          type="password"
          value={password}
          autoComplete="new-password"
          required
          invalid={passwordRule !== undefined}
          describedBy={passwordRule === undefined ? 'hint' : 'error'}
          onChange={(event) => setPassword(event.target.value)}
        />
      </Field>

      {completeSignup.isError && passwordRule === undefined && (
        <p role="alert" className="text-sm text-[var(--alert)]">
          {signupMessage(completeSignup.error, t)}
        </p>
      )}

      <Button
        type="submit"
        loading={completeSignup.isPending}
        loadingLabel={t('auth.signup.submitting')}
      >
        {t('auth.signup.submit')}
      </Button>
    </form>
  );
}
