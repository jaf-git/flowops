import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import {
  reauthenticationMessage,
  sessionLookupMessage,
  terminateSessionMessage,
} from '../api/authErrors';
import { useReauthenticate, useTerminateSession, useUserSessions } from '../hooks/useAuth';
import { SessionList } from './SessionList';

export function SessionAdministrationPanel(): JSX.Element {
  const { t } = useTranslation();
  const [typedUserId, setTypedUserId] = useState('');
  const [lookingAt, setLookingAt] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [terminatedOne, setTerminatedOne] = useState(false);

  const sessions = useUserSessions(lookingAt);
  const reauthenticateMutation = useReauthenticate();
  const terminateMutation = useTerminateSession();

  function look(event: FormEvent): void {
    event.preventDefault();
    setTerminatedOne(false);
    terminateMutation.reset();
    setLookingAt(typedUserId.trim());
  }

  function confirm(event: FormEvent): void {
    event.preventDefault();
    reauthenticateMutation.mutate(currentPassword, {
      onSettled: () => setCurrentPassword(''),
      onSuccess: () => {
        setConfirmed(true);

        terminateMutation.reset();
      },
    });
  }

  function terminate(reference: string): void {
    setTerminatedOne(false);
    terminateMutation.mutate(reference, {
      onSuccess: () => setTerminatedOne(true),

      onError: (failure) => {
        if (failure instanceof ApiError && failure.code === 'REAUTHENTICATION_REQUIRED') {
          setConfirmed(false);
        }
      },
    });
  }

  const lookupError = sessions.isError ? sessionLookupMessage(sessions.error, t) : undefined;
  const confirmError = reauthenticateMutation.isError
    ? reauthenticationMessage(reauthenticateMutation.error, t)
    : undefined;

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold text-[var(--ink)]">
          {t('auth.sessionAdministration.heading')}
        </h2>
        <p className="text-[var(--slate)]">{t('auth.sessionAdministration.explanation')}</p>
      </div>

      <form onSubmit={look} className="flex flex-col gap-4">
        <Field
          id="administered-user"
          label={t('auth.sessionAdministration.userId')}
          hint={t('auth.sessionAdministration.userIdHint')}
          error={lookupError}
          required
        >
          <Input
            id="administered-user"
            type="text"
            value={typedUserId}
            autoComplete="off"
            required
            invalid={lookupError !== undefined}
            describedBy={lookupError === undefined ? 'hint' : 'error'}
            onChange={(event) => setTypedUserId(event.target.value)}
          />
        </Field>
        <div style={{ alignSelf: 'start' }}>
          <Button type="submit" disabled={typedUserId.trim() === ''}>
            {t('auth.sessionAdministration.look')}
          </Button>
        </div>
      </form>

      {terminatedOne && (
        <p
          role="status"
          className="rounded-md bg-[var(--done-soft)] p-3 text-sm text-[var(--done)]"
        >
          {t('auth.sessionAdministration.terminated')}
        </p>
      )}

      {terminateMutation.isError && (
        <p
          role="alert"
          className="rounded-md bg-[var(--alert-soft)] p-3 text-sm text-[var(--alert)]"
        >
          {terminateSessionMessage(terminateMutation.error, t)}
        </p>
      )}

      {sessions.data !== undefined &&
        (confirmed ? (
          <SessionList
            sessions={sessions.data}
            onTerminate={terminate}
            terminatingReference={
              terminateMutation.isPending ? terminateMutation.variables : undefined
            }
          />
        ) : (
          <form onSubmit={confirm} className="flex flex-col gap-4">
            <SessionList sessions={sessions.data} />
            <Field
              id="administration-password"
              label={t('auth.reauthenticate.currentPassword')}
              hint={t('auth.sessionAdministration.whyConfirm')}
              error={confirmError}
              required
            >
              <Input
                id="administration-password"
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
        ))}
    </section>
  );
}
