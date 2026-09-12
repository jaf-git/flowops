import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { earliestDeadlineForInput } from '../../../shared/lib/serverClock';
import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Dialog } from '../../../shared/ui/Dialog';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { Tabs } from '../../../shared/ui/Tabs';
import {
  useAddTaskToInstance,
  useAssignablePeople,
  useAttachableTasks,
} from '../hooks/useProcesses';
import type { Instance } from '../api/processApi';

interface AddTaskToProcessDialogProps {
  open: boolean;
  instance: Instance;
  onClose: () => void;
  onAdded: () => void;
}

const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;

export function AddTaskToProcessDialog({
  open,
  instance,
  onClose,
  onAdded,
}: AddTaskToProcessDialogProps): JSX.Element {
  const { t } = useTranslation();
  const attachable = useAttachableTasks(open ? instance.id : null);
  const people = useAssignablePeople(open ? instance.id : null);
  const add = useAddTaskToInstance(instance.id);

  const [mode, setMode] = useState<'existing' | 'new'>('existing');
  const [taskId, setTaskId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [deadline, setDeadline] = useState('');
  const [priority, setPriority] = useState<string>('NORMAL');
  const [waitsFor, setWaitsFor] = useState<readonly string[]>([]);

  const offered = attachable.data?.tasks ?? [];
  const steerers = people.data?.people ?? [];

  function reset(): void {
    add.reset();
    setTaskId('');
    setTitle('');
    setDescription('');
    setAssigneeId('');
    setDeadline('');
    setPriority('NORMAL');
    setWaitsFor([]);
  }

  function close(): void {
    reset();
    onClose();
  }

  function toggleWaitsFor(stepId: string): void {
    setWaitsFor((chosen) =>
      chosen.includes(stepId) ? chosen.filter((each) => each !== stepId) : [...chosen, stepId],
    );
  }

  function submit(): void {
    add.mutate(
      mode === 'existing'
        ? { taskId, dependsOnStepIds: [...waitsFor] }
        : {
            newTask: {
              title,
              description: description === '' ? null : description,
              assigneeId,

              deadline: deadline === '' ? null : new Date(deadline).toISOString(),
              priority,
            },
            dependsOnStepIds: [...waitsFor],
          },
      {
        onSuccess: () => {
          reset();
          onAdded();
          onClose();
        },
      },
    );
  }

  const code = add.error instanceof ApiError ? add.error.code : undefined;
  const incomplete = mode === 'existing' ? taskId === '' : title.trim() === '' || assigneeId === '';

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('process.addTask.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.addTask.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={add.isPending}
            loadingLabel={t('process.addTask.submitting')}
          >
            {t('process.addTask.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.addTask.error.${code}`, t('process.addTask.error.UNKNOWN'))}
        </Banner>
      )}

      <Tabs
        label={t('process.addTask.modes')}
        active={mode}
        onSelect={(next) => setMode(next as 'existing' | 'new')}
        tabs={[
          { id: 'existing', label: t('process.addTask.mode.existing') },
          { id: 'new', label: t('process.addTask.mode.new') },
        ]}
      />

      {mode === 'existing' ? (
        <>
          {attachable.isError ? (
            <Banner tone="alert">{t('process.addTask.attachableLoadFailed')}</Banner>
          ) : offered.length === 0 && !attachable.isPending ? (
            <EmptyState
              heading={t('process.addTask.noneAttachable.heading')}
              body={t('process.addTask.noneAttachable.body')}
            />
          ) : (
            <Field id="add-task-existing" label={t('process.addTask.field.existing')} required>
              <Select
                id="add-task-existing"
                value={taskId}
                onChange={(event) => setTaskId(event.target.value)}
                options={[
                  { value: '', label: t('process.addTask.chooseTask') },
                  ...offered.map((task) => ({ value: task.id, label: task.title })),
                ]}
              />
            </Field>
          )}
          <Banner tone="info">{t('process.addTask.attachChangesNothing')}</Banner>
        </>
      ) : (
        <>
          <Field id="add-task-title" label={t('process.addTask.field.title')} required>
            <Input
              id="add-task-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              maxLength={200}
            />
          </Field>

          <Field id="add-task-description" label={t('process.addTask.field.description')}>
            <Input
              id="add-task-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </Field>

          <Field id="add-task-assignee" label={t('process.addTask.field.assignee')} required>
            <Select
              id="add-task-assignee"
              value={assigneeId}
              onChange={(event) => setAssigneeId(event.target.value)}
              options={[
                { value: '', label: t('process.addTask.choosePerson') },
                ...steerers.map((person) => ({
                  value: person.id,
                  label: person.displayName,
                })),
              ]}
            />
          </Field>

          <Field
            id="add-task-deadline"
            label={t('process.addTask.field.deadline')}
            hint={t('process.addTask.hint.deadlineOptional')}
          >
            <Input
              id="add-task-deadline"
              type="datetime-local"
              min={earliestDeadlineForInput()}
              value={deadline}
              onChange={(event) => setDeadline(event.target.value)}
            />
          </Field>

          <Field id="add-task-priority" label={t('process.addTask.field.priority')}>
            <Select
              id="add-task-priority"
              value={priority}
              onChange={(event) => setPriority(event.target.value)}
              options={PRIORITIES.map((value) => ({
                value,
                label: t(`task.priority.${value}`),
              }))}
            />
          </Field>
        </>
      )}

      <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
        <legend style={{ padding: 0 }}>{t('process.addTask.runsAfter')}</legend>
        <p style={{ color: 'var(--muted)', margin: '0 0 0.5rem' }}>
          {t('process.addTask.runsAfterHint')}
        </p>
        {instance.steps.map((step) => (
          <Checkbox
            key={step.id}
            id={`waits-for-${step.id}`}
            checked={waitsFor.includes(step.id)}
            onChange={() => toggleWaitsFor(step.id)}
            label={step.title}
          />
        ))}
      </fieldset>
    </Dialog>
  );
}
