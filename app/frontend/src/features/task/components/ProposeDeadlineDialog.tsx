import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { earliestDeadlineForInput } from '../../../shared/lib/serverClock';
import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Textarea } from '../../../shared/ui/Textarea';
import { useProposeDeadline } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

interface ProposeDeadlineDialogProps {
  taskId: string;
  onClose: () => void;
  onProposed: (task: Task) => void;
}

export function ProposeDeadlineDialog({
  taskId,
  onClose,
  onProposed,
}: ProposeDeadlineDialogProps): JSX.Element {
  const { t } = useTranslation();
  const propose = useProposeDeadline();
  const [date, setDate] = useState('');
  const [reason, setReason] = useState('');

  function close(): void {
    propose.reset();
    onClose();
  }

  function submit(): void {
    propose.mutate(
      { id: taskId, proposedDeadline: new Date(date).toISOString(), reason },
      {
        onSuccess: (task) => {
          propose.reset();
          setDate('');
          setReason('');
          onProposed(task);
          onClose();
        },
      },
    );
  }

  const code = propose.error instanceof ApiError ? propose.error.code : undefined;
  const noDate = date === '';
  const noReason = reason.trim() === '';

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.proposeDeadline.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.proposeDeadline.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={noDate || noReason}
            loading={propose.isPending}
            loadingLabel={t('task.proposeDeadline.submitting')}
          >
            {t('task.proposeDeadline.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.proposeDeadline.error.${code}`, t('task.proposeDeadline.error.UNKNOWN'))}
        </Banner>
      )}

      <Field
        id="propose-date"
        label={t('task.proposeDeadline.field.date')}
        hint={t('task.proposeDeadline.hint.date')}
        required
      >
        <Input
          id="propose-date"
          type="datetime-local"
          min={earliestDeadlineForInput()}
          value={date}
          onChange={(event) => setDate(event.target.value)}
          describedBy="hint"
        />
      </Field>

      <Field
        id="propose-reason"
        label={t('task.proposeDeadline.field.reason')}
        hint={t('task.proposeDeadline.hint.reason')}
        required
      >
        <Textarea
          id="propose-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>

      <p style={{ color: 'var(--ink-500)', fontSize: 'var(--text-body)' }}>
        {t('task.proposeDeadline.note')}
      </p>
    </Dialog>
  );
}
