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
import type { Instance, InstanceStep } from '../api/processApi';
import { useAssignablePeople, useAssignStep } from '../hooks/useProcesses';

function suggestionFor(step: InstanceStep | null): string {
  if (step === null || step.expectedDurationHours === null) {
    return '';
  }
  const due = new Date(Date.now() + step.expectedDurationHours * 60 * 60 * 1000);

  return new Date(due.getTime() - due.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

interface AssignStepDialogProps {
  instanceId: string;
  step: InstanceStep | null;
  onClose: () => void;
  onAssigned: (instance: Instance) => void;
}

export function AssignStepDialog({
  instanceId,
  step,
  onClose,
  onAssigned,
}: AssignStepDialogProps): JSX.Element {
  const { t } = useTranslation();

  const people = useAssignablePeople(step === null ? null : instanceId);
  const assign = useAssignStep(instanceId);

  const [assigneeId, setAssigneeId] = useState('');

  const [deadline, setDeadline] = useState(() => suggestionFor(step));

  function close(): void {
    assign.reset();
    setAssigneeId('');
    setDeadline(suggestionFor(step));
    onClose();
  }

  function submit(): void {
    if (step === null) {
      return;
    }
    assign.mutate(
      {
        stepId: step.id,
        assigneeId,

        deadline: deadline === '' ? null : new Date(deadline).toISOString(),
      },
      {
        onSuccess: (instance) => {
          onAssigned(instance);
          close();
        },
      },
    );
  }

  const code =
    assign.error instanceof ApiError ? assign.error.code : assign.error ? 'UNKNOWN' : undefined;
  const incomplete = assigneeId === '';

  return (
    <Dialog
      open={step !== null}
      onCancel={close}
      title={t('process.assign.title', { step: step?.title ?? '' })}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.assign.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={assign.isPending}
            loadingLabel={t('process.assign.submitting')}
          >
            {t('process.assign.confirm')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.assign.error.${code}`, t('process.assign.error.UNKNOWN'))}
        </Banner>
      )}

      {people.isError && <Banner tone="alert">{t('process.assign.peopleFailed')}</Banner>}

      <Field id="assign-person" label={t('process.assign.person')} required>
        <Select
          id="assign-person"
          value={assigneeId}
          onChange={(event) => setAssigneeId(event.target.value)}
          options={[
            { value: '', label: t('process.assign.choosePerson') },
            ...(people.data?.people ?? []).map((person) => ({
              value: person.id,
              label: person.displayName,
            })),
          ]}
        />
      </Field>

      <Field
        id="assign-deadline"
        label={t('process.assign.deadline')}
        hint={t('process.assign.hint.deadlineOptional')}
      >
        <Input
          id="assign-deadline"
          type="datetime-local"
          min={earliestDeadlineForInput()}
          value={deadline}
          onChange={(event) => setDeadline(event.target.value)}
        />
      </Field>
    </Dialog>
  );
}
