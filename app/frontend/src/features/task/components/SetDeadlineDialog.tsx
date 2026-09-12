import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { earliestDeadlineForInput } from '../../../shared/lib/serverClock';
import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { useSetDeadline } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

interface SetDeadlineDialogProps {
  taskId: string;

  currentDeadline: string | null;
  onClose: () => void;
  onSet: (task: Task) => void;
}

export function SetDeadlineDialog({
  taskId,
  currentDeadline,
  onClose,
  onSet,
}: SetDeadlineDialogProps): JSX.Element {
  const { t } = useTranslation();
  const set = useSetDeadline();

  const [date, setDate] = useState(currentDeadline === null ? '' : currentDeadline.slice(0, 16));

  function close(): void {
    set.reset();
    onClose();
  }

  function submit(): void {
    set.mutate(
      { id: taskId, deadline: new Date(date).toISOString() },
      {
        onSuccess: (task) => {
          set.reset();
          onSet(task);
          onClose();
        },
      },
    );
  }

  const code = set.error instanceof ApiError ? set.error.code : undefined;

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.setDeadline.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.setDeadline.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={date === ''}
            loading={set.isPending}
            loadingLabel={t('task.setDeadline.submitting')}
          >
            {t('task.setDeadline.confirm')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.setDeadline.error.${code}`, t('task.setDeadline.error.UNKNOWN'))}
        </Banner>
      )}

      <Field id="set-deadline-date" label={t('task.setDeadline.title')} required>
        <Input
          id="set-deadline-date"
          type="datetime-local"
          min={earliestDeadlineForInput()}
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </Field>
    </Dialog>
  );
}
