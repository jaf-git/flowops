import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Textarea } from '../../../shared/ui/Textarea';
import { useAbandonInstance } from '../hooks/useProcesses';

interface AbandonRunDialogProps {
  instanceId: string;

  name: string;

  inFlight: number;
  onClose: () => void;
  onAbandoned: () => void;
}

export function AbandonRunDialog({
  instanceId,
  name,
  inFlight,
  onClose,
  onAbandoned,
}: AbandonRunDialogProps): JSX.Element {
  const { t } = useTranslation();
  const abandon = useAbandonInstance(instanceId);
  const [reason, setReason] = useState('');

  function close(): void {
    abandon.reset();
    onClose();
  }

  function submit(): void {
    abandon.mutate(reason, {
      onSuccess: () => {
        abandon.reset();
        onAbandoned();
        onClose();
      },
    });
  }

  const code = abandon.error instanceof ApiError ? abandon.error.code : undefined;

  return (
    <Dialog
      open
      onCancel={close}
      title={t('process.abandon.title', { name })}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('process.abandon.cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={submit}
            disabled={reason.trim() === ''}
            loading={abandon.isPending}
            loadingLabel={t('process.abandon.submitting')}
          >
            {t('process.abandon.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`process.abandon.error.${code}`, t('process.abandon.error.UNKNOWN'))}
        </Banner>
      )}

      {inFlight > 0 && (
        <Banner tone="waiting">{t('process.abandon.survivors', { count: inFlight })}</Banner>
      )}

      <Field
        id="abandon-reason"
        label={t('process.abandon.field.reason')}
        hint={t('process.abandon.hint.reason')}
        required
      >
        <Textarea
          id="abandon-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={2000}
          describedBy="hint"
        />
      </Field>
    </Dialog>
  );
}
