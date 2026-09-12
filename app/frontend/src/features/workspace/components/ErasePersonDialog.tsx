import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { ConsequencePreview } from '../../../shared/ui/ConsequencePreview';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Spinner } from '../../../shared/ui/Spinner';
import type { Person } from '../api/workspaceApi';
import { useElevateSession } from '../hooks/useElevateSession';
import { useErasePerson, useErasurePreview } from '../hooks/usePeople';

interface ErasePersonDialogProps {
  person: Person | undefined;
  onClose: () => void;
  onErased: (personName: string) => void;
}

export function ErasePersonDialog({
  person,
  onClose,
  onErased,
}: ErasePersonDialogProps): JSX.Element | null {
  const { t } = useTranslation();
  const preview = useErasurePreview(person?.membershipId);
  const reauthenticate = useElevateSession();
  const erase = useErasePerson();
  const [password, setPassword] = useState('');
  const [typedName, setTypedName] = useState('');

  const membershipId = person?.membershipId;
  const [openedFor, setOpenedFor] = useState(membershipId);
  const [submittedFor, setSubmittedFor] = useState<string | undefined>(undefined);
  if (membershipId !== openedFor) {
    setOpenedFor(membershipId);
    setPassword('');
    setTypedName('');
    setSubmittedFor(undefined);
  }

  if (person === undefined) {
    return null;
  }

  const failure = submittedFor === membershipId ? (erase.error ?? reauthenticate.error) : null;
  const code = failure instanceof ApiError ? failure.code : undefined;

  const blocking =
    code === 'SUBJECT_ACTIVE'
      ? t('workspace.erase.stillActive', { name: person.displayName })
      : code === 'ONLY_OWNER'
        ? t('workspace.erase.onlyOwner')
        : preview.data?.refusal === 'SUBJECT_ACTIVE'
          ? t('workspace.erase.stillActive', { name: person.displayName })
          : preview.data?.refusal === 'ONLY_OWNER'
            ? t('workspace.erase.onlyOwner')
            : preview.isError
              ? t('workspace.erase.previewFailed')
              : undefined;

  const correctable =
    code === 'NAME_MISMATCH'
      ? t('workspace.erase.nameMismatch')
      : code === 'REAUTHENTICATION_REQUIRED' || code === 'AUTHENTICATION_REFUSED'
        ? t('workspace.erase.passwordWrong')
        : failure !== null && failure !== undefined && blocking === undefined
          ? t('workspace.erase.failed')
          : undefined;

  const working = reauthenticate.isPending || erase.isPending;

  const ready =
    blocking === undefined &&
    preview.data !== undefined &&
    password.trim() !== '' &&
    typedName.trim() !== '';

  function onConfirm(): void {
    if (person === undefined) {
      return;
    }

    setSubmittedFor(person.membershipId);
    reauthenticate.mutate(password, {
      onSuccess: () => {
        erase.mutate(
          { membershipId: person.membershipId, typedName },
          {
            onSuccess: () => {
              onErased(person.displayName);
              onClose();
            },
          },
        );
      },
    });
  }

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t('workspace.erase.title', { name: person.displayName })}
      actions={
        <>
          <Button type="button" variant="quiet" data-dialog-cancel onClick={onClose}>
            {blocking === undefined ? t('workspace.erase.cancel') : t('workspace.erase.close')}
          </Button>
          {blocking === undefined ? (
            <Button
              type="button"
              variant="destructive"
              disabled={!ready}
              loading={working}
              loadingLabel={t('workspace.erase.working')}
              onClick={onConfirm}
            >
              {t('workspace.erase.confirm')}
            </Button>
          ) : null}
        </>
      }
    >
      {blocking !== undefined ? (
        <Banner tone="alert">{blocking}</Banner>
      ) : preview.isPending ? (
        <Spinner label={t('workspace.erase.loading')} />
      ) : (
        <div style={{ display: 'grid', gap: 'var(--space-4)' }}>
          <ConsequencePreview
            heading={t('workspace.erase.heading', { name: person.displayName })}
            consequences={[
              ...(preview.data?.destroys ?? []).map((key) => t(`workspace.erase.destroys.${key}`)),
              ...(preview.data?.survives ?? []).map((key) => t(`workspace.erase.survives.${key}`)),
            ]}
          />

          <Banner tone="waiting">{t('workspace.erase.freeTextIsNotRewritten')}</Banner>

          {correctable === undefined ? null : <Banner tone="alert">{correctable}</Banner>}

          <Field id="erase-password" label={t('workspace.erase.passwordLabel')}>
            <Input
              id="erase-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>

          <Field
            id="erase-typed-name"
            label={t('workspace.erase.typedNameLabel', {
              name: preview.data?.displayName ?? person.displayName,
            })}
          >
            <Input
              id="erase-typed-name"

              autoFocus
              value={typedName}
              onChange={(event) => setTypedName(event.target.value)}
            />
          </Field>
        </div>
      )}
    </Dialog>
  );
}
