import { useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { earliestDeadlineForInput } from '../../../shared/lib/serverClock';
import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { useAssignablePeople, useCreateTask } from '../hooks/useTasks';
import type { AssignablePerson, Task, TaskPriority } from '../api/taskApi';

export interface ProcessPlacement {
  id: string;
  name: string;
}

interface CreateTaskDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (task: Task) => void;

  processes?: readonly ProcessPlacement[];

  onCreateInProcess?: (
    processId: string,
    task: {
      title: string;
      description: string | null;
      assigneeId: string;
      deadline: string | null;
      priority: TaskPriority;
    },
    outcome: { onSuccess: () => void; onError: (failure: unknown) => void },
  ) => void;

  placing?: boolean;

  onCreateAdHoc?: (
    task: {
      title: string;
      description: string | null;
      assigneeId: string;
      deadline: string | null;
      priority: TaskPriority;
    },
    outcome: { onSuccess: () => void; onError: (failure: unknown) => void },
  ) => void;

  prefill?: { title: string; description: string; assigneeId: string | undefined };

  assigneeLocked?: boolean;

  onCreateFromTemplate?: (
    templateId: string,
    task: {
      title: string;
      description: string | null;
      assigneeId: string;
      deadline: string | null;
      priority: TaskPriority;
    },
    outcome: { onSuccess: () => void; onError: (failure: unknown) => void },
  ) => void;

  templateSuggestions?: (context: {
    title: string;

    offer: boolean;

    onUse: (template: { id: string; title: string }) => void;
  }) => ReactNode;
}

const PRIORITIES: readonly TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

export function CreateTaskDialog({
  open,
  onClose,
  onCreated,
  processes,
  onCreateInProcess,
  placing = false,
  prefill,
  onCreateAdHoc,
  assigneeLocked = false,
  onCreateFromTemplate,
  templateSuggestions,
}: CreateTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const people = useAssignablePeople();
  const create = useCreateTask();

  const [title, setTitle] = useState(prefill?.title ?? '');
  const [description, setDescription] = useState(prefill?.description ?? '');
  const [assigneeId, setAssigneeId] = useState(prefill?.assigneeId ?? '');
  const [deadline, setDeadline] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('NORMAL');

  const [processId, setProcessId] = useState('');

  const [kind, setKind] = useState<'TASK' | 'TICKET'>('TASK');

  const [templateId, setTemplateId] = useState<string | undefined>(undefined);

  const [placementFailure, setPlacementFailure] = useState<unknown>(undefined);

  const assignable: readonly AssignablePerson[] = people.data?.people ?? [];

  function close(): void {
    create.reset();
    setPlacementFailure(undefined);
    onClose();
  }

  function submit(): void {
    if (processId !== '' && onCreateInProcess !== undefined) {
      setPlacementFailure(undefined);
      onCreateInProcess(
        processId,
        {
          title,
          description: description === '' ? null : description,
          assigneeId,
          deadline: deadline === '' ? null : new Date(deadline).toISOString(),
          priority,
        },
        {
          onSuccess: () => {
            setTitle('');
            setDescription('');
            setAssigneeId('');
            setDeadline('');
            setPriority('NORMAL');
            setProcessId('');
            onClose();
          },
          onError: setPlacementFailure,
        },
      );
      return;
    }

    if (onCreateAdHoc !== undefined) {
      setPlacementFailure(undefined);
      onCreateAdHoc(
        {
          title,
          description: description === '' ? null : description,
          assigneeId,
          deadline: deadline === '' ? null : new Date(deadline).toISOString(),
          priority,
        },
        {
          onSuccess: () => {
            setTitle('');
            setDescription('');
            setAssigneeId('');
            setDeadline('');
            setPriority('NORMAL');
            setProcessId('');
            onClose();
          },
          onError: setPlacementFailure,
        },
      );
      return;
    }

    if (templateId !== undefined && onCreateFromTemplate !== undefined) {
      onCreateFromTemplate(
        templateId,
        {
          title: title.trim(),
          description: description.trim() === '' ? null : description.trim(),
          assigneeId,
          deadline: deadline === '' ? null : new Date(deadline).toISOString(),
          priority,
        },
        {
          onSuccess: () => {
            setTitle('');
            setDescription('');
            setAssigneeId('');
            setDeadline('');
            setPriority('NORMAL');
            setTemplateId(undefined);
            onClose();
          },
          onError: setPlacementFailure,
        },
      );
      return;
    }

    create.mutate(
      {
        title,
        description,
        assigneeId,

        deadline: deadline === '' ? null : new Date(deadline).toISOString(),
        priority,
        templateId,
        kind,
      },
      {
        onSuccess: (task) => {
          create.reset();
          setTitle('');
          setDescription('');
          setAssigneeId('');
          setDeadline('');
          setPriority('NORMAL');
          setProcessId('');
          setKind('TASK');
          setTemplateId(undefined);
          onCreated(task);
          onClose();
        },
      },
    );
  }

  const failure = placementFailure ?? create.error;
  const code = failure instanceof ApiError ? failure.code : undefined;
  const incomplete = title.trim() === '' || assigneeId === '';

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('task.create.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.create.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={create.isPending || placing}
            loadingLabel={t('task.create.submitting')}
          >
            {t('task.create.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.create.error.${code}`, t('task.create.error.UNKNOWN'))}
        </Banner>
      )}

      <fieldset className="fo-kind-switch">
        <legend className="fo-visually-hidden">{t('task.create.kindLegend')}</legend>
        <button
          type="button"
          className="fo-kind-switch-option"
          aria-pressed={kind === 'TASK'}
          onClick={() => setKind('TASK')}
        >
          {t('task.create.kindTask')}
        </button>
        <button
          type="button"
          className="fo-kind-switch-option"
          aria-pressed={kind === 'TICKET'}
          onClick={() => {
            setKind('TICKET');

            setTemplateId(undefined);
          }}
        >
          {t('task.create.kindTicket')}
        </button>
      </fieldset>

      <p className="fo-kind-switch-hint">
        {kind === 'TASK' ? t('task.create.kindTaskHint') : t('task.create.kindTicketHint')}
      </p>

      <Field id="task-title" label={t('task.create.field.title')} required>
        <Input
          id="task-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={200}
        />
      </Field>

      {onCreateFromTemplate === undefined
        ? null
        : templateSuggestions?.({
            title,

            offer: kind === 'TASK' && templateId === undefined,
            onUse: (template) => {
              setTitle(template.title);
              setTemplateId(template.id);
            },
          })}

      {kind === 'TASK' && (
        <Field
          id="task-description"
          label={t('task.create.field.description')}
          hint={t('task.create.hint.description')}
        >
          <Input
            id="task-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </Field>
      )}

      <Field id="task-assignee" label={t('task.create.field.assignee')} required>
        {assigneeLocked ? (
          <p
            id="task-assignee"
            style={{ margin: 0, padding: 'var(--gap-2) 0', color: 'var(--ink-800)' }}
          >
            {assignable.find((person) => person.id === assigneeId)?.displayName ??
              t('task.create.choosePerson')}
          </p>
        ) : (
          <Select
            id="task-assignee"
            value={assigneeId}
            onChange={(event) => setAssigneeId(event.target.value)}
            options={[
              { value: '', label: t('task.create.choosePerson') },
              ...assignable.map((person) => ({ value: person.id, label: person.displayName })),
            ]}
          />
        )}
      </Field>

      {kind === 'TASK' && (
        <Field
          id="task-deadline"
          label={t('task.create.field.deadline')}
          hint={t('task.create.hint.deadlineOptional')}
        >
          <Input
            id="task-deadline"
            type="datetime-local"
            min={earliestDeadlineForInput()}
            value={deadline}
            onChange={(event) => setDeadline(event.target.value)}
          />
        </Field>
      )}

      {kind === 'TASK' &&
      processes !== undefined &&
      processes.length > 0 &&
      onCreateInProcess !== undefined ? (
        <Field
          id="task-process"
          label={t('task.create.field.process')}
          hint={t('task.create.hint.process')}
        >
          <Select
            id="task-process"
            value={processId}
            onChange={(event) => setProcessId(event.target.value)}
            options={[
              { value: '', label: t('task.create.noProcess') },
              ...processes.map((run) => ({ value: run.id, label: run.name })),
            ]}
          />
        </Field>
      ) : null}

      {kind === 'TASK' && (
        <Field id="task-priority" label={t('task.create.field.priority')}>
          <Select
            id="task-priority"
            value={priority}
            onChange={(event) => setPriority(event.target.value as TaskPriority)}
            options={PRIORITIES.map((value) => ({ value, label: t(`task.priority.${value}`) }))}
          />
        </Field>
      )}
    </Dialog>
  );
}
