import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Select } from '../../../shared/ui/Select';
import { Textarea } from '../../../shared/ui/Textarea';
import type { TaskLifecycleState } from '../../../shared/ui/TaskActionRail';
import { useOverrideTask } from '../hooks/useTasks';
import type { Task } from '../api/taskApi';

const OPENS_PHASE: Record<TaskLifecycleState, string> = {
  CREATED: 'WAIT',
  ACCEPTED: 'WAIT',
  IN_PROGRESS: 'ACTIVE',
  BLOCKED: 'BLOCKED',
  COMPLETED: 'REVIEW',
  APPROVED: 'APPROVAL',
  CLOSED: '',
};

const STATES = Object.keys(OPENS_PHASE) as TaskLifecycleState[];

interface OverrideTaskDialogProps {
  taskId: string;

  state: TaskLifecycleState;
  onClose: () => void;
  onOverridden: (task: Task) => void;
}

export function OverrideTaskDialog({
  taskId,
  state,
  onClose,
  onOverridden,
}: OverrideTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const override = useOverrideTask();
  const [targetState, setTargetState] = useState<TaskLifecycleState | ''>('');
  const [reason, setReason] = useState('');

  function close(): void {
    override.reset();
    onClose();
  }

  function submit(): void {
    if (targetState === '') {
      return;
    }
    override.mutate(
      { id: taskId, targetState, reason },
      {
        onSuccess: (task) => {
          override.reset();
          onOverridden(task);
          onClose();
        },
      },
    );
  }

  const code = override.error instanceof ApiError ? override.error.code : undefined;
  const incomplete = targetState === '' || reason.trim() === '';

  const targets = STATES.filter((candidate) => candidate !== state);

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.override.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.override.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={override.isPending}
            loadingLabel={t('task.override.submitting')}
          >
            {t('task.override.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.override.error.${code}`, t('task.override.error.UNKNOWN'))}
        </Banner>
      )}

      <Banner tone="waiting">{t('task.override.warning')}</Banner>

      <Field id="override-state" label={t('task.override.field.state')} required>
        <Select
          id="override-state"
          value={targetState}
          onChange={(event) => setTargetState(event.target.value as TaskLifecycleState)}
          options={[
            { value: '', label: t('task.override.field.choose') },
            ...targets.map((candidate) => ({
              value: candidate,
              label: t(`task.state.${candidate}`),
            })),
          ]}
        />
      </Field>

      {targetState !== '' && (
        <p style={{ margin: 0, color: 'var(--ink-700)', fontSize: 'var(--text-body)' }}>
          {consequence(t, state, targetState)}
        </p>
      )}

      <Field
        id="override-reason"
        label={t('task.override.field.reason')}
        hint={t('task.override.hint.reason')}
        required
      >
        <Textarea
          id="override-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>
    </Dialog>
  );
}

function consequence(
  t: (key: string, options?: Record<string, string>) => string,
  from: TaskLifecycleState,
  to: TaskLifecycleState,
): string {
  const closing = OPENS_PHASE[from];
  const opening = OPENS_PHASE[to];
  if (closing === '') {
    return t('task.override.consequence.opensOnly', { to: t(`task.phaseName.${opening}`) });
  }
  if (opening === '') {
    return t('task.override.consequence.closesOnly', { from: t(`task.phaseName.${closing}`) });
  }
  return t('task.override.consequence.both', {
    from: t(`task.phaseName.${closing}`),
    to: t(`task.phaseName.${opening}`),
  });
}
