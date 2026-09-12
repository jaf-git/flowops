import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { ConsequencePreview } from '../../../shared/ui/ConsequencePreview';
import { Dialog } from '../../../shared/ui/Dialog';
import { useRemoveTaskFromInstance } from '../hooks/useProcesses';
import { stepsWaitingOn, type Instance, type InstanceStep } from '../api/processApi';

interface RemoveTaskFromProcessDialogProps {
  open: boolean;
  instance: Instance;
  step: InstanceStep | null;
  onClose: () => void;
}

export function RemoveTaskFromProcessDialog({
  open,
  instance,
  step,
  onClose,
}: RemoveTaskFromProcessDialogProps): JSX.Element | null {
  const { t } = useTranslation();
  const remove = useRemoveTaskFromInstance(instance.id);

  if (step === null) {
    return null;
  }

  const opening = stepsWaitingOn(instance, step);
  const code = remove.error instanceof ApiError ? remove.error.code : undefined;

  function close(): void {
    remove.reset();
    onClose();
  }

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('process.removeTask.title', { title: step.title })}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel autoFocus>
            {t('process.removeTask.cancel')}
          </Button>
          <Button
            loading={remove.isPending}
            loadingLabel={t('process.removeTask.submitting')}
            onClick={() => {
              remove.mutate(step.id, { onSuccess: close });
            }}
          >
            {t('process.removeTask.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.removeTask.error.${code}`, t('process.removeTask.error.UNKNOWN'))}
        </Banner>
      )}

      <ConsequencePreview
        heading={t('process.removeTask.consequences', { title: step.title })}
        consequences={[
          step.planned
            ? t('process.removeTask.plannedStepGoes')
            : t('process.removeTask.taskSurvives'),
          opening.length === 0
            ? t('process.removeTask.nothingWaits')
            : t('process.removeTask.opens', {
                count: opening.length,
                titles: opening.map((each) => each.title).join(', '),
              }),
        ]}
      />
    </Dialog>
  );
}
