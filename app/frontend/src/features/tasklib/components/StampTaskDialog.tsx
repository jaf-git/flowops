import { useState, type JSX } from 'react';
import type { AssignablePerson } from '../../../shared/model/people';
import { useTranslation } from 'react-i18next';

import { earliestDeadlineForInput } from '../../../shared/lib/serverClock';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import type {
  MetadataField,
  StampTaskInput,
  TaskTemplate,
  TemplatePriority,
} from '../api/taskTemplateApi';
import { MetadataAsk } from './MetadataAsk';

const PRIORITIES: readonly TemplatePriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

export interface MetadataAnswer {
  field: MetadataField;
  answer: string;
}

interface StampTaskDialogProps {
  template: TaskTemplate;
  onClose: () => void;
  onStamp: (input: StampTaskInput, metadata?: MetadataAnswer) => void;
  busy: boolean;

  permissions: readonly string[];

  assignablePeople: readonly AssignablePerson[];
}

export function StampTaskDialog({
  template,
  onClose,
  onStamp,
  busy,
  assignablePeople,
  permissions,
}: StampTaskDialogProps): JSX.Element {
  const { t } = useTranslation();
  const [title, setTitle] = useState(template.title);
  const [assigneeId, setAssigneeId] = useState('');
  const [deadline, setDeadline] = useState('');
  const [priority, setPriority] = useState<TemplatePriority>(template.priority);
  const [metadataAnswer, setMetadataAnswer] = useState('');

  const assignable = assignablePeople;

  const asking =
    permissions.includes('TASK_TEMPLATE_METADATA') && template.metadata.nextAsk !== null
      ? template.metadata.nextAsk
      : undefined;

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t('tasklib.stamp.title', { title: template.title })}
      actions={
        <>
          <Button variant="quiet" onClick={onClose} disabled={busy} data-dialog-cancel>
            {t('tasklib.form.cancel')}
          </Button>
          <Button
            variant="primary"
            disabled={busy || assigneeId === '' || title.trim() === ''}
            onClick={() =>
              onStamp(
                {
                  title: title.trim(),
                  assigneeId,

                  deadline: deadline === '' ? undefined : new Date(deadline).toISOString(),
                  priority,
                },

                asking !== undefined && metadataAnswer.trim() !== ''
                  ? { field: asking, answer: metadataAnswer.trim() }
                  : undefined,
              )
            }
          >
            {t('tasklib.stamp.confirm')}
          </Button>
        </>
      }
    >
      <p className="fo-stamp-lead">{t('tasklib.stamp.lead')}</p>

      <Field id="stamp-assignee" label={t('tasklib.stamp.assignee')} required>
        <Select
          id="stamp-assignee"
          value={assigneeId}
          onChange={(event) => setAssigneeId(event.target.value)}
          options={[
            { value: '', label: t('tasklib.stamp.choosePerson') },
            ...assignable.map((person) => ({ value: person.id, label: person.displayName })),
          ]}
        />
      </Field>

      {asking !== undefined && (
        <MetadataAsk field={asking} value={metadataAnswer} onChange={setMetadataAnswer} />
      )}

      <Field
        id="stamp-title"
        label={t('tasklib.stamp.taskTitle')}
        hint={t('tasklib.stamp.titleHint')}
      >
        <Input
          id="stamp-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={200}
        />
      </Field>

      <Field
        id="stamp-deadline"
        label={t('tasklib.stamp.deadline')}
        hint={t('tasklib.stamp.deadlineHint')}
      >
        <Input
          id="stamp-deadline"
          type="datetime-local"
          min={earliestDeadlineForInput()}
          value={deadline}
          onChange={(event) => setDeadline(event.target.value)}
        />
      </Field>

      <Field id="stamp-priority" label={t('tasklib.stamp.priority')}>
        <Select
          id="stamp-priority"
          value={priority}
          onChange={(event) => setPriority(event.target.value as TemplatePriority)}
          options={PRIORITIES.map((value) => ({ value, label: t(`task.priority.${value}`) }))}
        />
      </Field>

      {template.checklist.length > 0 && (
        <div className="fo-stamp-checklist">
          <p className="fo-form-group">{t('tasklib.stamp.willCarry')}</p>
          <ul>
            {template.checklist.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}
    </Dialog>
  );
}
