import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { ConsequencePreview } from '../../../shared/ui/ConsequencePreview';
import { Dialog } from '../../../shared/ui/Dialog';
import type { Person } from '../api/workspaceApi';
import { useDeactivatePerson } from '../hooks/usePeople';

interface DeactivatePersonDialogProps {
  person: Person | undefined;

  people: Person[];
  onClose: () => void;
  onDeactivated: (personName: string) => void;
}

export function DeactivatePersonDialog({
  person,
  people,
  onClose,
  onDeactivated,
}: DeactivatePersonDialogProps): JSX.Element | null {
  const { t } = useTranslation();
  const deactivate = useDeactivatePerson();

  if (person === undefined) {
    return null;
  }

  const directReports = people.filter(
    (candidate) => candidate.managerId === person.membershipId && candidate.status === 'ACTIVE',
  );
  const newManager = people.find((candidate) => candidate.membershipId === person.managerId);

  const refusal =
    deactivate.error instanceof ApiError && deactivate.error.code === 'ONLY_OWNER'
      ? t('workspace.deactivate.onlyOwner')
      : deactivate.error !== null
        ? t('workspace.deactivate.failed')
        : undefined;

  function onConfirm(): void {
    if (person === undefined) {
      return;
    }
    deactivate.mutate(person.membershipId, {
      onSuccess: () => {
        onDeactivated(person.displayName);
        onClose();
      },
    });
  }

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t('workspace.deactivate.title', { name: person.displayName })}
      initialFocus="cancel"
      actions={
        <>
          <Button type="button" variant="quiet" data-dialog-cancel onClick={onClose}>
            {refusal === undefined
              ? t('workspace.deactivate.cancel')
              : t('workspace.deactivate.close')}
          </Button>
          {refusal === undefined ? (
            <Button
              type="button"
              variant="destructive"
              loading={deactivate.isPending}
              loadingLabel={t('workspace.deactivate.working')}
              onClick={onConfirm}
            >
              {t('workspace.deactivate.confirm')}
            </Button>
          ) : null}
        </>
      }
    >
      {refusal === undefined ? (
        <ConsequencePreview
          heading={t('workspace.deactivate.heading', { name: person.displayName })}
          consequences={[
            t('workspace.deactivate.accessEnds', { name: person.displayName }),
            t('workspace.deactivate.sessionsEnd'),
            directReports.length === 0
              ? t('workspace.deactivate.noReports')
              : t('workspace.deactivate.reportsMove', {
                  count: directReports.length,
                  names: directReports.map((report) => report.displayName).join(', '),
                  manager: newManager?.displayName ?? '',
                }),
            t('workspace.deactivate.workIsKept'),
          ]}
        />
      ) : (
        <Banner tone="alert">{refusal}</Banner>
      )}
    </Dialog>
  );
}
