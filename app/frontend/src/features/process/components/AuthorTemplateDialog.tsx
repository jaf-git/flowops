import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Textarea } from '../../../shared/ui/Textarea';
import type { NewTemplateStep, Template } from '../api/processApi';
import { useAuthorTemplate } from '../hooks/useProcesses';
import { TaskTemplatePicker } from './TaskTemplatePicker';

interface AuthorTemplateDialogProps {
  open: boolean;
  onClose: () => void;
  onAuthored: (template: Template) => void;
}

interface DraftStep {
  key: number;
  taskTemplateId: string;
  expectedDurationHours: string;

  optional: boolean;
  conditionNote: string;
}

function blank(key: number): DraftStep {
  return { key, taskTemplateId: '', expectedDurationHours: '', optional: false, conditionNote: '' };
}

export function AuthorTemplateDialog({
  open,
  onClose,
  onAuthored,
}: AuthorTemplateDialogProps): JSX.Element {
  const { t } = useTranslation();
  const author = useAuthorTemplate();

  const [name, setName] = useState('');
  const [overview, setOverview] = useState('');
  const [steps, setSteps] = useState<DraftStep[]>([blank(0)]);
  const [nextKey, setNextKey] = useState(1);

  function close(): void {
    author.reset();
    setName('');
    setOverview('');
    setSteps([blank(0)]);
    setNextKey(1);
    onClose();
  }

  function amend(key: number, change: Partial<DraftStep>): void {
    setSteps((current) =>
      current.map((step) => (step.key === key ? { ...step, ...change } : step)),
    );
  }

  function submit(): void {
    const payload: NewTemplateStep[] = steps.map((step) => ({
      taskTemplateId: step.taskTemplateId,
      expectedDurationHours:
        step.expectedDurationHours === '' ? null : Number(step.expectedDurationHours),
      optional: step.optional,

      conditionNote:
        step.optional && step.conditionNote.trim() !== '' ? step.conditionNote.trim() : null,
    }));

    author.mutate(
      { name, overview: overview === '' ? null : overview, steps: payload },
      {
        onSuccess: (created) => {
          onAuthored(created);
          close();
        },
      },
    );
  }

  const failure = author.error instanceof ApiError ? author.error : undefined;

  const code =
    failure?.code ?? (author.error === null || author.error === undefined ? undefined : 'UNKNOWN');

  const untitled =
    failure?.code === 'STEP_TASK_TEMPLATE_REQUIRED'
      ? Number(/^steps\[(\d+)]/.exec(failure.details[0]?.field ?? '')?.[1] ?? -1)
      : -1;

  const incomplete = name.trim() === '' || steps.some((step) => step.taskTemplateId === '');

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('process.author.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.author.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={author.isPending}
            loadingLabel={t('process.author.submitting')}
          >
            {t('process.author.confirm')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.author.error.${code}`, t('process.author.error.UNKNOWN'))}
        </Banner>
      )}

      <Field id="template-name" label={t('process.author.name')} required>
        <Input
          id="template-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={200}
        />
      </Field>

      <Field id="template-overview" label={t('process.author.overview')}>
        <Textarea
          id="template-overview"
          rows={2}
          value={overview}
          onChange={(event) => setOverview(event.target.value)}
        />
      </Field>

      <ol aria-label={t('process.author.steps')}>
        {steps.map((step, position) => (
          <li key={step.key}>
            <Field
              id={`step-work-${String(step.key)}`}
              label={t('process.author.stepTitle', { position: position + 1 })}
              error={
                position === untitled ? t('process.author.error.STEP_TASK_TEMPLATE_REQUIRED') : ''
              }
              required
            >
              <TaskTemplatePicker
                id={`step-work-${String(step.key)}`}
                invalid={position === untitled}
                value={step.taskTemplateId}
                onChange={(taskTemplateId) => {
                  amend(step.key, { taskTemplateId });
                }}
              />
            </Field>

            <Field
              id={`step-hours-${String(step.key)}`}
              label={t('process.author.stepHours')}
              hint={t('process.author.stepHoursHint')}
            >
              <Input
                id={`step-hours-${String(step.key)}`}
                type="number"
                min={1}
                value={step.expectedDurationHours}
                onChange={(event) => {
                  amend(step.key, { expectedDurationHours: event.target.value });
                }}
              />
            </Field>

            <Checkbox
              id={`step-optional-${String(step.key)}`}
              label={t('process.optionalStep.markOptional')}
              checked={step.optional}
              onChange={(optional) => {
                amend(step.key, { optional, conditionNote: optional ? step.conditionNote : '' });
              }}
            />

            {step.optional ? (
              <Field
                id={`step-condition-${String(step.key)}`}
                label={t('process.optionalStep.conditionNote')}
                hint={t('process.optionalStep.conditionHint')}
              >
                <Input
                  id={`step-condition-${String(step.key)}`}
                  value={step.conditionNote}
                  maxLength={500}
                  onChange={(event) => {
                    amend(step.key, { conditionNote: event.target.value });
                  }}
                />
              </Field>
            ) : null}

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
          </li>
        ))}
      </ol>

      <Button
        variant="quiet"
        onClick={() => {
          setSteps((current) => [...current, blank(nextKey)]);
          setNextKey(nextKey + 1);
        }}
      >
        {t('process.author.addStep')}
      </Button>
    </Dialog>
  );
}
