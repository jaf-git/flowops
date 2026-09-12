import type { FormEvent, JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Button } from '../../../shared/ui/Button';
import { Field } from '../../../shared/ui/Field';
import { Icon } from '../../../shared/ui/Icon';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import type { SetupPrefill } from '../api/workspaceApi';
import type { SetupDraftHandle } from '../hooks/useSetupDraft';
import { useSetUpWorkspace } from '../hooks/useWorkspaceSetup';
import { timezoneWasDetected } from '../model/timezone';

interface WorkspaceSetupFormProps {
  prefill: SetupPrefill;
  handle: SetupDraftHandle;
}

export function WorkspaceSetupForm({ prefill, handle }: WorkspaceSetupFormProps): JSX.Element {
  const { t } = useTranslation();
  const setUp = useSetUpWorkspace();
  const { draft, change } = handle;

  const detected = timezoneWasDetected(prefill.availableTimezones);
  const refusedField = setUp.error instanceof ApiError ? setUp.error.details[0]?.field : undefined;

  function submit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    setUp.mutate(draft);
  }

  return (
    <form
      onSubmit={submit}
      noValidate
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-5)',
        width: '100%',
        maxWidth: '480px',
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        <h2
          style={{
            fontSize: 'var(--text-2xl)',
            fontWeight: 600,
            lineHeight: 1.3,
            color: 'var(--ink)',
            margin: 0,
          }}
        >
          {t('workspace.setup.heading')}
        </h2>
        <p
          style={{
            color: 'var(--slate)',
            fontSize: 'var(--text-base)',
            lineHeight: 1.5,
            margin: 0,
          }}
        >
          {t('workspace.setup.body')}
        </p>
      </div>

      <Field
        id="ownerName"
        label={t('workspace.setup.ownerName.label')}
        hint={t('workspace.setup.ownerName.hint')}
        error={refusedField === 'ownerName' ? t('workspace.setup.ownerName.required') : undefined}
        required
      >
        <Input
          id="ownerName"
          name="ownerName"
          value={draft.ownerName}
          autoComplete="name"
          onChange={(event) => change('ownerName', event.target.value)}
          invalid={refusedField === 'ownerName'}
          describedBy={refusedField === 'ownerName' ? 'error' : 'hint'}
        />
      </Field>

      <Field
        id="workspaceName"
        label={t('workspace.setup.workspaceName.label')}
        hint={t('workspace.setup.workspaceName.hint')}
        error={
          refusedField === 'workspaceName' ? t('workspace.setup.workspaceName.required') : undefined
        }
        required
      >
        <Input
          id="workspaceName"
          name="workspaceName"
          value={draft.workspaceName}
          autoComplete="organization"
          onChange={(event) => change('workspaceName', event.target.value)}
          invalid={refusedField === 'workspaceName'}
          describedBy={refusedField === 'workspaceName' ? 'error' : 'hint'}
        />
      </Field>

      <fieldset
        style={{
          border: 0,
          padding: 0,
          margin: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-2)',
        }}
      >
        <legend
          style={{ color: 'var(--slate)', fontSize: 'var(--text-md)', fontWeight: 600, padding: 0 }}
        >
          {t('workspace.setup.use.label')}
        </legend>
        <div style={{ display: 'flex', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
          {(['WORK', 'PERSONAL'] as const).map((option) => (
            <label
              key={option}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 'var(--space-2)',
                color: 'var(--ink)',
                fontSize: 'var(--text-base)',
              }}
            >
              <input
                type="radio"
                name="use"
                value={option}
                checked={draft.use === option}
                onChange={() => change('use', option)}
              />
              {t(`workspace.setup.use.${option}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <Field
        id="timezone"
        label={t('workspace.setup.timezone.label')}
        hint={
          detected
            ? t('workspace.setup.timezone.detected')
            : t('workspace.setup.timezone.notDetected')
        }
        error={refusedField === 'timezone' ? t('workspace.setup.timezone.unknown') : undefined}
        required
      >
        <Select
          id="timezone"
          name="timezone"
          value={draft.timezone}
          options={prefill.availableTimezones}
          onChange={(event) => change('timezone', event.target.value)}
          invalid={refusedField === 'timezone'}
          describedBy={refusedField === 'timezone' ? 'error' : 'hint'}
        />
      </Field>

      {setUp.isError && refusedField === undefined && (
        <p
          role="alert"
          style={{ color: 'var(--alert)', fontSize: 'var(--text-md)', lineHeight: 1.5, margin: 0 }}
        >
          {t('workspace.setup.failed')}
        </p>
      )}

      <Button
        type="submit"
        loading={setUp.isPending}
        loadingLabel={t('workspace.setup.submitting')}
      >
        {t('workspace.setup.submit')}
        <Icon name="arrow" size={16} />
      </Button>
    </form>
  );
}
