import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Textarea } from '../../../shared/ui/Textarea';
import { useFormaliseNode } from '../hooks/useAnalysis';

export function FormaliseNodeDialog({
  nodeId,
  open,
  onClose,
}: {
  nodeId: string;
  open: boolean;
  onClose: () => void;
}): JSX.Element {
  const { t } = useTranslation();
  const formalise = useFormaliseNode();

  const [title, setTitle] = useState('');
  const [detail, setDetail] = useState('');
  const [steps, setSteps] = useState('');

  const titled = title.trim().length > 0;

  const written = formalise.isSuccess;

  function close(): void {
    setTitle('');
    setDetail('');
    setSteps('');
    formalise.reset();
    onClose();
  }

  return (
    <Dialog
      open={open}
      onCancel={close}
      title={t('pipeline.formalise.title')}

      initialFocus="content"
      actions={
        written ? (
          <Button variant="quiet" onClick={close}>
            {t('pipeline.formalise.done')}
          </Button>
        ) : (
          <>
            <Button variant="quiet" onClick={close}>
              {t('pipeline.formalise.cancel')}
            </Button>
            <Button
              variant="accent"
              loading={formalise.isPending}
              loadingLabel={t('pipeline.formalise.saving')}
              disabled={!titled}
              onClick={() => {
                formalise.mutate({
                  nodeId,
                  title: title.trim(),
                  detail: detail.trim() === '' ? undefined : detail.trim(),
                  steps: steps
                    .split('\n')
                    .map((line) => line.trim())
                    .filter((line) => line !== ''),
                });
              }}
            >
              {t('pipeline.formalise.save')}
            </Button>
          </>
        )
      }
    >
      {written ? (
        <p className="fo-formalise-done" role="status">
          {t('pipeline.formalise.written', { title: title.trim() })}
        </p>
      ) : (
        <p className="fo-formalise-lede">{t('pipeline.formalise.lede')}</p>
      )}

      {written ? null : (
        <>
          <Field id="fo-formalise-title" label={t('pipeline.formalise.name')} required>
            <Input
              id="fo-formalise-title"
              value={title}
              maxLength={200}
              onChange={(event) => {
                setTitle(event.target.value);
              }}
            />
          </Field>

          <Field id="fo-formalise-detail" label={t('pipeline.formalise.detail')}>
            <Textarea
              id="fo-formalise-detail"
              value={detail}
              maxLength={4000}
              onChange={(event) => {
                setDetail(event.target.value);
              }}
            />
          </Field>

          <Field
            id="fo-formalise-steps"
            label={t('pipeline.formalise.steps')}
            hint={t('pipeline.formalise.stepsHint')}
          >
            <Textarea
              id="fo-formalise-steps"
              value={steps}
              describedBy="hint"
              onChange={(event) => {
                setSteps(event.target.value);
              }}
            />
          </Field>
        </>
      )}

      {formalise.isError ? (
        <p className="fo-formalise-failed" role="alert">
          {t('pipeline.formalise.failed')}
        </p>
      ) : null}
    </Dialog>
  );
}
