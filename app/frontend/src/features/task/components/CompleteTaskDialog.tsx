import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Textarea } from '../../../shared/ui/Textarea';
import { useCompleteTask } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

interface CompleteTaskDialogProps {
  taskId: string;
  onClose: () => void;
  onCompleted: (task: Task) => void;
}

export function CompleteTaskDialog({
  taskId,
  onClose,
  onCompleted,
}: CompleteTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const complete = useCompleteTask();
  const [note, setNote] = useState('');
  const [externalLink, setExternalLink] = useState('');

  function close(): void {
    complete.reset();
    onClose();
  }

  function submit(): void {
    complete.mutate(
      { id: taskId, note, externalLink },
      {
        onSuccess: (task) => {
          complete.reset();
          setNote('');
          setExternalLink('');
          onCompleted(task);
          onClose();
        },
      },
    );
  }

  const code = complete.error instanceof ApiError ? complete.error.code : undefined;
  const nothingShown = note.trim() === '';

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.complete.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.complete.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={nothingShown}
            loading={complete.isPending}
            loadingLabel={t('task.complete.submitting')}
          >
            {t('task.complete.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.complete.error.${code}`, t('task.complete.error.UNKNOWN'))}
        </Banner>
      )}

      <Field
        id="complete-note"
        label={t('task.complete.field.note')}
        hint={t('task.complete.hint.note')}
        required
      >
        <Textarea
          id="complete-note"
          value={note}
          onChange={(event) => setNote(event.target.value)}
          maxLength={4000}
          rows={4}
          describedBy="hint"
        />
      </Field>

      <Field
        id="complete-link"
        label={t('task.complete.field.link')}
        hint={t('task.complete.hint.link')}
      >
        <Input
          id="complete-link"
          value={externalLink}
          onChange={(event) => setExternalLink(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>
    </Dialog>
  );
}
