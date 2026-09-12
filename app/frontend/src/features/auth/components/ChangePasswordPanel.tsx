import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { changePasswordMessage, reauthenticationMessage } from '../api/authErrors';
import { useChangePassword, useReauthenticate } from '../hooks/useAuth';

export function ChangePasswordPanel(): JSX.Element {
  const { t } = useTranslation();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [changed, setChanged] = useState(false);

  const reauthenticateMutation = useReauthenticate();
  const changePasswordMutation = useChangePassword();

  function confirm(event: FormEvent): void {
    event.preventDefault();
    reauthenticateMutation.mutate(currentPassword, {
      onSettled: () => setCurrentPassword(''),
      onSuccess: () => {
        setConfirmed(true);
        setChanged(false);

        changePasswordMutation.reset();
      },
    });
  }

  function submit(event: FormEvent): void {
    event.preventDefault();
    changePasswordMutation.mutate(newPassword, {
      onSuccess: () => {
        setNewPassword('');
        setConfirmed(false);
        setChanged(true);
        reauthenticateMutation.reset();
      },

      onError: (failure) => {
        if (failure instanceof ApiError && failure.status === 403) {
          setNewPassword('');
          setConfirmed(false);
        }
      },
    });
  }

  const changeError = changePasswordMutation.isError
    ? changePasswordMessage(changePasswordMutation.error, t)
    : undefined;
  const confirmError = reauthenticateMutation.isError
    ? reauthenticationMessage(reauthenticateMutation.error, t)
    : undefined;

  return (
    <section className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold text-[var(--ink)]">
          {t('auth.changePassword.heading')}
        </h2>
        <p className="text-[var(--slate)]">{t('auth.changePassword.otherSessionsWarning')}</p>
      </div>

      {changed && (
        <p
          role="status"
          className="rounded-md bg-[var(--done-soft)] p-3 text-sm text-[var(--done)]"
        >
          {t('auth.changePassword.changed')}
        </p>
      )}

      {confirmed ? (
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Field
            id="new-password"
            label={t('auth.changePassword.newPassword')}
            hint={t('auth.changePassword.policyHint')}
            error={changeError}
            required
          >
            <Input
              id="new-password"
              type="password"
              value={newPassword}
              autoComplete="new-password"
              required
              invalid={changeError !== undefined}
              describedBy={changeError === undefined ? 'hint' : 'error'}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </Field>
          <div style={{ alignSelf: 'start' }}>
            <Button
              type="submit"
              loading={changePasswordMutation.isPending}
              loadingLabel={t('auth.changePassword.submitting')}
            >
              {t('auth.changePassword.submit')}
            </Button>
          </div>
        </form>
      ) : (
        <form onSubmit={confirm} className="flex flex-col gap-4">
          <Field
            id="current-password"
            label={t('auth.reauthenticate.currentPassword')}
            hint={t('auth.reauthenticate.why')}
            error={confirmError}
            required
          >
            <Input
              id="current-password"
              type="password"
              value={currentPassword}
              autoComplete="current-password"
              required
              invalid={confirmError !== undefined}
              describedBy={confirmError === undefined ? 'hint' : 'error'}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </Field>
          <div style={{ alignSelf: 'start' }}>
            <Button
              type="submit"
              loading={reauthenticateMutation.isPending}
              loadingLabel={t('auth.reauthenticate.submitting')}
            >
              {t('auth.reauthenticate.submit')}
            </Button>
          </div>
        </form>
      )}
    </section>
  );
}
