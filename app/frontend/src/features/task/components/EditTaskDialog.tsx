import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { earliestDeadlineForInput } from '../../../shared/lib/serverClock';
import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { Textarea } from '../../../shared/ui/Textarea';
import { useEditTask } from '../hooks/useTasks';
import type { Task, TaskPriority } from '../api/taskApi';

interface EditTaskDialogProps {
  task: Task;
  onClose: () => void;
  onEdited: (task: Task) => void;
}

const PRIORITIES: TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

function forTheControl(instant: string | null): string {
  if (instant === null) {
    return '';
  }
  const at = new Date(instant);
  const shifted = new Date(at.getTime() - at.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 16);
}

export function EditTaskDialog({ task, onClose, onEdited }: EditTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const edit = useEditTask();
  const [deadline, setDeadline] = useState(forTheControl(task.deadline));
  const [priority, setPriority] = useState<TaskPriority>(task.priority);
  const [description, setDescription] = useState(task.description ?? '');

  function close(): void {
    edit.reset();
    onClose();
  }

  function submit(): void {
    edit.mutate(
      { id: task.id, deadline: new Date(deadline).toISOString(), priority, description },
      {
        onSuccess: (updated) => {
          edit.reset();
          onEdited(updated);
          onClose();
        },
      },
    );
  }

  const code = edit.error instanceof ApiError ? edit.error.code : undefined;

  const deadlineMoved = deadline !== forTheControl(task.deadline);
  const priorityMoved = priority !== task.priority;
  const descriptionMoved = description.trim() !== (task.description ?? '').trim();
  const nothingDiffers = !deadlineMoved && !priorityMoved && !descriptionMoved;

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.edit.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.edit.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={nothingDiffers}
            loading={edit.isPending}
            loadingLabel={t('task.edit.submitting')}
          >
            {t('task.edit.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">{t(`task.edit.error.${code}`, t('task.edit.error.UNKNOWN'))}</Banner>
      )}

      <Field id="edit-deadline" label={t('task.edit.field.deadline')} required>
        <Input
          id="edit-deadline"
          type="datetime-local"
          min={earliestDeadlineForInput()}
          value={deadline}
          onChange={(event) => setDeadline(event.target.value)}
        />
      </Field>

      <Field id="edit-priority" label={t('task.edit.field.priority')} required>
        <Select
          id="edit-priority"
          value={priority}
          onChange={(event) => setPriority(event.target.value as TaskPriority)}
          options={PRIORITIES.map((option) => ({
            value: option,
            label: t(`task.priority.${option}`),
          }))}
        />
      </Field>

      <Field id="edit-description" label={t('task.edit.field.description')}>
        <Textarea
          id="edit-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          maxLength={4000}
        />
      </Field>

      <section aria-label={t('task.edit.preview.title')}>
        <h3>{t('task.edit.preview.title')}</h3>
        {nothingDiffers ? (
          <p>{t('task.edit.preview.none')}</p>
        ) : (
          <ul>
            {deadlineMoved && (
              <li>
                {t('task.edit.preview.deadline')}: {t('task.edit.preview.from')}{' '}
                {forTheControl(task.deadline)} {t('task.edit.preview.to')} {deadline}
              </li>
            )}
            {priorityMoved && (
              <li>
                {t('task.edit.preview.priority')}: {t('task.edit.preview.from')}{' '}
                {t(`task.priority.${task.priority}`)} {t('task.edit.preview.to')}{' '}
                {t(`task.priority.${priority}`)}
              </li>
            )}
            {descriptionMoved && <li>{t('task.edit.preview.description')}</li>}
          </ul>
        )}
        {deadlineMoved && <p>{t('task.edit.notice.deadline')}</p>}
      </section>

      <p style={{ color: 'var(--ink-500)', fontSize: 'var(--text-body)' }}>
        {t('task.edit.notice.locked')}
      </p>
    </Dialog>
  );
}
