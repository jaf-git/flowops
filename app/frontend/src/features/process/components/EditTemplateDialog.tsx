import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { ConsequencePreview } from '../../../shared/ui/ConsequencePreview';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { IconButton } from '../../../shared/ui/IconButton';
import { Input } from '../../../shared/ui/Input';
import { Textarea } from '../../../shared/ui/Textarea';
import type { NewTemplateStep, Template } from '../api/processApi';
import { useEditTemplate } from '../hooks/useProcesses';
import { TaskTemplatePicker } from './TaskTemplatePicker';

interface EditTemplateDialogProps {
  open: boolean;
  template: Template;
  onClose: () => void;
  onSaved: () => void;
}

interface Draft {
  key: number;
  id?: string;
  taskTemplateId: string;

  title: string;
  hours: string;
}

export function EditTemplateDialog({
  open,
  template,
  onClose,
  onSaved,
}: EditTemplateDialogProps): JSX.Element {
  const { t } = useTranslation();
  const save = useEditTemplate(template.id);

  const [overview, setOverview] = useState(template.overview ?? '');
  const [steps, setSteps] = useState<Draft[]>(() => fromTemplate(template));
  const [nextKey, setNextKey] = useState(template.steps.length);

  function close(): void {
    save.reset();
    setOverview(template.overview ?? '');
    setSteps(fromTemplate(template));
    setNextKey(template.steps.length);
    onClose();
  }

  function amend(key: number, change: Partial<Draft>): void {
    setSteps((current) =>
      current.map((each) => (each.key === key ? { ...each, ...change } : each)),
    );
  }

  function move(from: number, to: number): void {
    setSteps((current) => {
      const moved = current[from];
      if (moved === undefined) {
        return current;
      }
      const next = [...current];
      next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
  }

  function submit(): void {
    save.mutate(
      {
        overview: overview.trim() === '' ? null : overview,
        steps: steps.map((step): NewTemplateStep => {
          const sent: NewTemplateStep = {
            taskTemplateId: step.taskTemplateId,

            expectedDurationHours: step.hours === '' ? null : Number(step.hours),
          };

          return step.id === undefined ? sent : { ...sent, id: step.id };
        }),
      },
      {
        onSuccess: () => {
          onSaved();
          close();
        },
      },
    );
  }

  const code =
    save.error instanceof ApiError ? save.error.code : save.error ? 'UNKNOWN' : undefined;

  const untitled = steps.findIndex((step) => step.taskTemplateId === '');

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('process.edit.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.edit.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={untitled !== -1}
            loading={save.isPending}
            loadingLabel={t('process.edit.submitting')}
          >
            {t('process.edit.confirm')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.edit.error.${code}`, t('process.edit.error.UNKNOWN'))}
        </Banner>
      )}

      <ConsequencePreview
        heading={t('process.edit.title')}
        consequences={[t('process.edit.unaffected')]}
      />

      <Field id="edit-template-overview" label={t('process.author.overview')}>
        <Textarea
          id="edit-template-overview"
          rows={2}
          value={overview}
          onChange={(event) => setOverview(event.target.value)}
        />
      </Field>

      <ol aria-label={t('process.author.steps')}>
        {steps.map((step, position) => (
          <li key={step.key}>
            <Field
              id={`edit-step-work-${String(step.key)}`}
              label={t('process.author.stepTitle', { position: position + 1 })}
              error={
                position === untitled ? t('process.author.error.STEP_TASK_TEMPLATE_REQUIRED') : ''
              }
              required
            >
              <TaskTemplatePicker
                id={`edit-step-work-${String(step.key)}`}
                invalid={position === untitled}
                value={step.taskTemplateId}
                onChange={(taskTemplateId) => {
                  amend(step.key, { taskTemplateId });
                }}
              />
            </Field>

            <Field
              id={`edit-step-hours-${String(step.key)}`}
              label={t('process.author.stepHours')}
              hint={t('process.author.stepHoursHint')}
            >
              <Input
                id={`edit-step-hours-${String(step.key)}`}
                type="number"
                min={1}
                value={step.hours}
                onChange={(event) => {
                  amend(step.key, { hours: event.target.value });
                }}
              />
            </Field>

            <span style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <IconButton
                icon="chevronUp"
                label={t('process.edit.up', { title: step.title })}
                disabled={position === 0}
                onClick={() => {
                  move(position, position - 1);
                }}
              />
              <IconButton
                icon="chevronDown"
                label={t('process.edit.down', { title: step.title })}
                disabled={position === steps.length - 1}
                onClick={() => {
                  move(position, position + 1);
                }}
              />
              {steps.length > 1 ? (
                <Button
                  variant="quiet"
                  onClick={() => {
                    setSteps((current) => current.filter((each) => each.key !== step.key));
                  }}
                >
                  {t('process.edit.removeStep')}
                </Button>
              ) : null}
            </span>
          </li>
        ))}
      </ol>

      <p style={{ color: 'var(--muted)' }}>{t('process.edit.removeTakesEdges')}</p>

      <Button
        variant="quiet"
        onClick={() => {
          setSteps((current) => [
            ...current,
            { key: nextKey, taskTemplateId: '', title: '', hours: '' },
          ]);
          setNextKey(nextKey + 1);
        }}
      >
        {t('process.author.addStep')}
      </Button>
    </Dialog>
  );
}

function fromTemplate(template: Template): Draft[] {
  return template.steps.map((step, index) => ({
    key: index,
    id: step.id,
    taskTemplateId: step.taskTemplateId,
    title: step.title ?? '',
    hours: step.expectedDurationHours === null ? '' : String(step.expectedDurationHours),
  }));
}
