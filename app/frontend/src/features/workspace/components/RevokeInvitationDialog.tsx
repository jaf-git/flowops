import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { i18next } from '../../../i18n';
import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { ConsequencePreview } from '../../../shared/ui/ConsequencePreview';
import { Dialog } from '../../../shared/ui/Dialog';
import type { PendingInvitation, RevokedInvitation } from '../api/workspaceApi';
import { useRevokeInvitation } from '../hooks/usePeople';

interface RevokeInvitationDialogProps {
  invitation: PendingInvitation | undefined;
  onClose: () => void;

  onRevoked: (result: RevokedInvitation) => void;
}

export function RevokeInvitationDialog({
  invitation,
  onClose,
  onRevoked,
}: RevokeInvitationDialogProps): JSX.Element | null {
  const { t } = useTranslation();
  const revoke = useRevokeInvitation();

  function close(): void {
    revoke.reset();
    onClose();
  }

  function confirm(): void {
    if (invitation === undefined) {
      return;
    }
    revoke.mutate(invitation.id, {
      onSuccess: (result) => {
        revoke.reset();
        onRevoked(result);
        onClose();
      },
    });
  }

  const conflict = revoke.error instanceof ApiError && revoke.error.code === 'ALREADY_ACCEPTED';

  return (
    <Dialog
      open={invitation !== undefined}
      onCancel={close}
      title={t('workspace.revoke.title')}
      initialFocus="cancel"
      actions={
        <>
          <Button type="button" variant="quiet" data-dialog-cancel onClick={close}>
            {conflict ? t('workspace.revoke.close') : t('workspace.revoke.cancel')}
          </Button>
          {conflict ? null : (
            <Button
              type="button"
              variant="destructive"
              onClick={confirm}
              loading={revoke.isPending}
              loadingLabel={t('workspace.revoke.withdrawing')}
            >
              {t('workspace.revoke.confirm')}
            </Button>
          )}
        </>
      }
    >
      {conflict ? (
        <Banner tone="alert">
          {t('workspace.revoke.error.ALREADY_ACCEPTED', { name: acceptedBy(revoke.error) })}
        </Banner>
      ) : (
        <>
          {revoke.isError ? (
            <Banner tone="alert">{t('workspace.revoke.error.unexpected')}</Banner>
          ) : null}
          <ConsequencePreview
            heading={t('workspace.revoke.heading', { address: invitation?.emailAddress ?? '' })}
            consequences={[
              t('workspace.revoke.consequence.cannotJoin'),
              t('workspace.revoke.consequence.linkStops'),
              t('workspace.revoke.consequence.canInviteAgain'),
            ]}
          />
        </>
      )}
    </Dialog>
  );
}

function acceptedBy(failure: Error | null): string {
  const name =
    failure instanceof ApiError
      ? failure.details.find((violation) => violation.field === 'member')?.rule
      : undefined;
  return name !== undefined && name !== '' ? name : i18next.t('workspace.revoke.somebody');
}
