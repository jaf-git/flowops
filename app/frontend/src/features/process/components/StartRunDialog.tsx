import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import type { Candidate, Instance, Template } from '../api/processApi';
import { useStartInstance } from '../hooks/useProcesses';

interface StartRunDialogProps {
  template: Template | null;

  steerers: readonly Candidate[];
  onClose: () => void;

  onStarted: (instance: Instance) => void;
}

export function StartRunDialog({
  template,
  steerers,
  onClose,
  onStarted,
}: StartRunDialogProps): JSX.Element {
  const { t } = useTranslation();
  const start = useStartInstance();

  const [name, setName] = useState('');
  const [processOwnerId, setProcessOwnerId] = useState('');

  function close(): void {
    start.reset();
    setName('');
    setProcessOwnerId('');
    onClose();
  }

  function submit(): void {
    if (template === null) {
      return;
    }
    start.mutate(
      { templateId: template.id, name, processOwnerId },
      {
        onSuccess: (started) => {
          onStarted(started);
          close();
        },
      },
    );
  }

  const code =
    start.error instanceof ApiError ? start.error.code : start.error ? 'UNKNOWN' : undefined;
  const incomplete = name.trim() === '' || processOwnerId === '';

  return (
    <Dialog
      open={template !== null}
      onCancel={close}
      title={t('process.start.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.start.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={incomplete}
            loading={start.isPending}
            loadingLabel={t('process.start.submitting')}
          >
            {t('process.start.confirm')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.start.error.${code}`, t('process.start.error.UNKNOWN'))}
        </Banner>
      )}

      <p
        style={{
          margin: 0,
          borderInlineStart: '2px solid var(--brand-line)',
          paddingInlineStart: 'var(--space-3)',
          color: 'var(--brand-dark)',
          fontSize: 'var(--text-md)',
          fontWeight: 500,
        }}
      >
        {template?.name}
      </p>

      <Field
        id="run-name"
        label={t('process.start.name')}
        hint={t('process.start.nameHint')}
        required
      >
        <Input
          id="run-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={200}
        />
      </Field>

      <Field
        id="run-owner"
        label={t('process.start.owner')}
        hint={t('process.start.ownerHint')}
        required
      >
        <Select
          id="run-owner"
          value={processOwnerId}
          onChange={(event) => setProcessOwnerId(event.target.value)}
          options={[
            { value: '', label: t('process.start.choosePerson') },
            ...steerers.map((person) => ({ value: person.id, label: person.displayName })),
          ]}
        />
      </Field>
    </Dialog>
  );
}
