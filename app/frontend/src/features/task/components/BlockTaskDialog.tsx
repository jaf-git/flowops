import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Textarea } from '../../../shared/ui/Textarea';
import { useBlockTask } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

interface BlockTaskDialogProps {
  taskId: string;
  onClose: () => void;
  onBlocked: (task: Task) => void;
}

export function BlockTaskDialog({ taskId, onClose, onBlocked }: BlockTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const block = useBlockTask();
  const [reason, setReason] = useState('');

  function close(): void {
    block.reset();
    onClose();
  }

  function submit(): void {
    block.mutate(
      { id: taskId, reason },
      {
        onSuccess: (task) => {
          block.reset();
          setReason('');
          onBlocked(task);
          onClose();
        },
      },
    );
  }

  const code = block.error instanceof ApiError ? block.error.code : undefined;
  const nothingSaid = reason.trim() === '';

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.block.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.block.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={nothingSaid}
            loading={block.isPending}
            loadingLabel={t('task.block.submitting')}
          >
            {t('task.block.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">{t(`task.block.error.${code}`, t('task.block.error.UNKNOWN'))}</Banner>
      )}

      <Field
        id="block-reason"
        label={t('task.block.field.reason')}
        hint={t('task.block.hint.reason')}
        required
      >
        <Textarea
          id="block-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>
    </Dialog>
  );
}
