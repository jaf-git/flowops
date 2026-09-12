import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import { Spinner } from '../../../shared/ui/Spinner';
import {
  useAddChecklistItem,
  useAttachLink,
  useDetachLink,
  useRemoveChecklistItem,
  useTaskMaterial,
  useTickChecklistItem,
} from '../hooks/useTasks';
import type { LinkRole } from '../api/taskApi';

interface TaskMaterialPanelProps {
  taskId: string;

  mine: boolean;
}

const ROLES: LinkRole[] = ['INPUT', 'OUTPUT', 'REFERENCE'];

const REFUSALS: Readonly<Record<string, string>> = {
  LINK_SCHEME_NOT_ALLOWED: 'task.material.error.scheme',
  REQUEST_INVALID: 'task.material.error.invalid',
  TASK_IS_CLOSED: 'task.material.error.closed',
  NOT_THE_ASSIGNEE: 'task.material.error.notYours',
  TASK_NOT_FOUND: 'task.material.error.gone',
};

function refusalKey(failure: unknown): string {
  if (failure instanceof ApiError) {
    const known = REFUSALS[failure.code];
    if (known !== undefined) {
      return known;
    }
  }
  return 'task.material.error.unknown';
}

export function TaskMaterialPanel({ taskId, mine }: TaskMaterialPanelProps): JSX.Element | null {
  const { t } = useTranslation();
  const material = useTaskMaterial(taskId);
  const attach = useAttachLink();
  const detach = useDetachLink();
  const addStep = useAddChecklistItem();
  const tick = useTickChecklistItem();
  const removeStep = useRemoveChecklistItem();

  const [url, setUrl] = useState('');
  const [label, setLabel] = useState('');
  const [role, setRole] = useState<LinkRole>('INPUT');
  const [stepText, setStepText] = useState('');

  const [addingLink, setAddingLink] = useState(false);
  const [addingStep, setAddingStep] = useState(false);

  if (material.isPending) {
    return <Spinner label={t('task.material.loading')} />;
  }

  if (material.isError) {
    const code = material.error instanceof ApiError ? material.error.code : undefined;

    if (code === 'NOT_THE_ASSIGNEE') {
      return (
        <p style={{ margin: 0, color: 'var(--ink-500)', fontSize: 'var(--text-body)' }}>
          {t('task.material.notYours')}
        </p>
      );
    }

    return code === 'TASK_IS_CLOSED' ? (
      <p style={{ margin: 0, color: 'var(--ink-500)', fontSize: 'var(--text-body)' }}>
        {t('task.material.closed')}
      </p>
    ) : (
      <Banner tone="alert">{t('task.material.loadFailed')}</Banner>
    );
  }

  const links = material.data?.links ?? [];
  const checklist = material.data?.checklist ?? [];
  const done = checklist.filter((item) => item.done).length;

  const failure =
    attach.error ?? detach.error ?? addStep.error ?? tick.error ?? removeStep.error ?? undefined;

  function submitLink(): void {
    attach.mutate(
      { id: taskId, url, label, role },
      {
        onSuccess: () => {
          setUrl('');
          setLabel('');
        },
      },
    );
  }

  function submitStep(): void {
    addStep.mutate({ id: taskId, text: stepText }, { onSuccess: () => setStepText('') });
  }

  return (
    <section style={{ display: 'grid', gap: 'var(--gap-4)' }}>
      {failure !== undefined && <Banner tone="alert">{t(refusalKey(failure))}</Banner>}

      {ROLES.map((group) => {
        const inGroup = links.filter((link) => link.role === group);

        if (inGroup.length === 0) {
          return null;
        }
        return (
          <div key={group} style={{ display: 'grid', gap: 'var(--gap-2)' }}>
            <h3 className="fo-eyebrow">{t(`task.material.role.${group}`)}</h3>
            {inGroup.map((link) => (
              <div
                key={link.id}
                style={{ display: 'flex', alignItems: 'center', gap: 'var(--gap-2)' }}
              >
                <a
                  href={link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ flex: '1 1 auto' }}
                >
                  {link.displayText}
                </a>
                <Button
                  variant="quiet"
                  onClick={() => detach.mutate({ id: taskId, linkId: link.id })}
                >
                  {t('task.material.detach')}
                </Button>
              </div>
            ))}
          </div>
        );
      })}

      {!addingLink ? (
        <Button
          variant="quiet"
          onClick={() => {
            setAddingLink(true);
          }}
        >
          {t('task.material.attach')}
        </Button>
      ) : (
        <div
          style={{
            display: 'grid',
            gap: 'var(--gap-2)',
          }}
        >
          <Field id="material-url" label={t('task.material.field.url')}>
            <Input
              id="material-url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://"
            />
          </Field>
          <Field id="material-label" label={t('task.material.field.label')}>
            <Input
              id="material-label"
              value={label}
              onChange={(event) => setLabel(event.target.value)}
            />
          </Field>
          <Field id="material-role" label={t('task.material.field.role')}>
            <Select
              id="material-role"
              value={role}
              onChange={(event) => setRole(event.target.value as LinkRole)}
              options={ROLES.map((option) => ({
                value: option,
                label: t(`task.material.role.${option}`),
              }))}
            />
          </Field>
          <div style={{ display: 'flex', gap: 'var(--gap-2)' }}>
            <Button onClick={submitLink} disabled={url.trim() === ''} loading={attach.isPending}>
              {t('task.material.attach')}
            </Button>
            <Button
              variant="quiet"
              onClick={() => {
                setAddingLink(false);
                setUrl('');
                setLabel('');
              }}
            >
              {t('task.material.cancel')}
            </Button>
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gap: 'var(--gap-2)' }}>
        {checklist.length > 0 && (
          <h3 className="fo-eyebrow">
            {t('task.material.progress', { done, total: checklist.length })}
          </h3>
        )}

        {checklist.map((item) => (
          <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: 'var(--gap-2)' }}>
            <span style={{ flex: '1 1 auto' }}>
              <Checkbox
                id={`step-${item.id}`}
                label={item.text}
                checked={item.done}

                struck={item.done}

                disabled={!mine || (tick.isPending && tick.variables?.itemId === item.id)}
                onChange={(checked) => tick.mutate({ id: taskId, itemId: item.id, done: checked })}
              />
            </span>
            <Button
              variant="quiet"
              onClick={() => removeStep.mutate({ id: taskId, itemId: item.id })}
            >
              {t('task.material.removeStep')}
            </Button>
          </div>
        ))}

        {!addingStep ? (
          <Button
            variant="quiet"
            onClick={() => {
              setAddingStep(true);
            }}
          >
            {t('task.material.addStep')}
          </Button>
        ) : (
          <div style={{ display: 'grid', gap: 'var(--gap-2)' }}>
            <Field id="material-step" label={t('task.material.field.step')}>
              <Input
                id="material-step"
                value={stepText}
                onChange={(event) => setStepText(event.target.value)}

                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    submitStep();
                  }
                }}
              />
            </Field>
            <div style={{ display: 'flex', gap: 'var(--gap-2)' }}>
              <Button
                onClick={submitStep}
                disabled={stepText.trim() === ''}
                loading={addStep.isPending}
              >
                {t('task.material.addStep')}
              </Button>
              <Button
                variant="quiet"
                onClick={() => {
                  setAddingStep(false);
                  setStepText('');
                }}
              >
                {t('task.material.cancel')}
              </Button>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
