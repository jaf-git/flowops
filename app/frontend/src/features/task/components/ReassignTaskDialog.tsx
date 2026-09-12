import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Select } from '../../../shared/ui/Select';
import { Textarea } from '../../../shared/ui/Textarea';
import { useAssignablePeople, useReassignTask } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

interface ReassignTaskDialogProps {
  taskId: string;

  currentAssigneeId?: string | null;
  onClose: () => void;
  onReassigned: (task: Task) => void;
}

export function ReassignTaskDialog({
  taskId,
  currentAssigneeId = null,
  onClose,
  onReassigned,
}: ReassignTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const reassign = useReassignTask();
  const people = useAssignablePeople();
  const [newAssigneeId, setNewAssigneeId] = useState('');
  const [reason, setReason] = useState('');

  function close(): void {
    reassign.reset();
    onClose();
  }

  function submit(): void {
    reassign.mutate(
      { id: taskId, newAssigneeId, reason },
      {
        onSuccess: (task) => {
          reassign.reset();
          onReassigned(task);
          onClose();
        },
      },
    );
  }

  const code = reassign.error instanceof ApiError ? reassign.error.code : undefined;
  const candidates = (people.data?.people ?? []).filter(
    (person) => person.id !== currentAssigneeId,
  );
  const incomplete = newAssigneeId === '' || reason.trim() === '';

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.reassign.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.reassign.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={reassign.isPending}
            loadingLabel={t('task.reassign.submitting')}
          >
            {t('task.reassign.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.reassign.error.${code}`, t('task.reassign.error.UNKNOWN'))}
        </Banner>
      )}

      <p style={{ margin: 0, color: 'var(--ink-700)', fontSize: 'var(--text-body)' }}>
        {t('task.reassign.consequence')}
      </p>

      <Field id="reassign-person" label={t('task.reassign.field.person')} required>
        <Select
          id="reassign-person"
          value={newAssigneeId}
          onChange={(event) => setNewAssigneeId(event.target.value)}
          options={[
            { value: '', label: t('task.reassign.field.choose') },
            ...candidates.map((person) => ({ value: person.id, label: person.displayName })),
          ]}
        />
      </Field>

      <Field
        id="reassign-reason"
        label={t('task.reassign.field.reason')}
        hint={t('task.reassign.hint.reason')}
        required
      >
        <Textarea
          id="reassign-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>
    </Dialog>
  );
}
