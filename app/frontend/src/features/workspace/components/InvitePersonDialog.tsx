import { useId, useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { inviteFailureMessage } from '../api/inviteErrors';
import type { InvitedRole, Person } from '../api/workspaceApi';
import { useInvitePerson } from '../hooks/usePeople';

interface InvitePersonDialogProps {
  open: boolean;
  onClose: () => void;

  managers: readonly Person[];
}

export function InvitePersonDialog({
  open,
  onClose,
  managers,
}: InvitePersonDialogProps): JSX.Element {
  const { t } = useTranslation();
  const fieldId = useId();
  const invite = useInvitePerson();

  const [emailAddress, setEmailAddress] = useState('');
  const [role, setRole] = useState<InvitedRole>('EMPLOYEE');
  const [managerId, setManagerId] = useState(managers[0]?.membershipId ?? '');

  function close(): void {
    invite.reset();
    setEmailAddress('');
    setRole('EMPLOYEE');

    setManagerId(managers[0]?.membershipId ?? '');
    onClose();
  }

  function submit(event: FormEvent): void {
    event.preventDefault();
    invite.mutate(
      {
        emailAddress: emailAddress.trim(),
        role,
        managerId: managerId || (managers[0]?.membershipId ?? ''),
      },
      { onSuccess: close },
    );
  }

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('workspace.invite.title')}
      initialFocus="content"
      actions={
        <>
          <Button type="button" variant="quiet" onClick={close}>
            {t('workspace.invite.cancel')}
          </Button>

          <Button
            type="submit"
            form={`${fieldId}-form`}
            loading={invite.isPending}
            loadingLabel={t('workspace.invite.sending')}
          >
            {t('workspace.invite.send')}
          </Button>
        </>
      }
    >
      <form
        id={`${fieldId}-form`}
        onSubmit={submit}
        style={{ display: 'grid', gap: 'var(--space-4)' }}
      >
        {invite.isError ? (
          <Banner tone="alert">{inviteFailureMessage(invite.error, t)}</Banner>
        ) : null}

        <Field id={`${fieldId}-email`} label={t('workspace.invite.emailLabel')} required>
          <Input
            id={`${fieldId}-email`}
            type="email"
            required
            autoComplete="off"
            value={emailAddress}
            onChange={(event) => setEmailAddress(event.target.value)}
          />
        </Field>

        <Field id={`${fieldId}-role`} label={t('workspace.invite.roleLabel')} required>
          <Select
            id={`${fieldId}-role`}
            value={role}
            onChange={(event) => setRole(event.target.value as InvitedRole)}
            options={[
              { value: 'EMPLOYEE', label: t('workspace.role.EMPLOYEE') },
              { value: 'MANAGER', label: t('workspace.role.MANAGER') },
            ]}
          />
        </Field>

        <Field
          id={`${fieldId}-manager`}
          label={t('workspace.invite.managerLabel')}
          hint={managers.length === 1 ? t('workspace.invite.managerOnlyYou') : undefined}
          required
        >
          <Select
            id={`${fieldId}-manager`}
            value={managerId || (managers[0]?.membershipId ?? '')}
            onChange={(event) => setManagerId(event.target.value)}
            describedBy={managers.length === 1 ? 'hint' : undefined}
            options={managers.map((manager) => ({
              value: manager.membershipId,
              label: manager.displayName,
            }))}
          />
        </Field>
      </form>
    </Dialog>
  );
}
