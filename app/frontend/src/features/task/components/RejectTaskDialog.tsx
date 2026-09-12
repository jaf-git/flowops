import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Textarea } from '../../../shared/ui/Textarea';
import { useRejectTask } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

interface RejectTaskDialogProps {
  taskId: string;
  onClose: () => void;
  onRejected: (task: Task) => void;
}

export function RejectTaskDialog({
  taskId,
  onClose,
  onRejected,
}: RejectTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const reject = useRejectTask();
  const [reason, setReason] = useState('');

  function close(): void {
    reject.reset();
    onClose();
  }

  function submit(): void {
    reject.mutate(
      { id: taskId, reason },
      {
        onSuccess: (task) => {
          reject.reset();
          setReason('');
          onRejected(task);
          onClose();
        },
      },
    );
  }

  const code = reject.error instanceof ApiError ? reject.error.code : undefined;
  const nothingSaid = reason.trim() === '';

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.reject.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.reject.cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={submit}
            disabled={nothingSaid}
            loading={reject.isPending}
            loadingLabel={t('task.reject.submitting')}
          >
            {t('task.reject.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.reject.error.${code}`, t('task.reject.error.UNKNOWN'))}
        </Banner>
      )}

      <Field
        id="reject-reason"
        label={t('task.reject.field.reason')}
        hint={t('task.reject.hint.reason')}
        required
      >
        <Textarea
          id="reject-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>
    </Dialog>
  );
}
