import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';

interface TemplateKindDialogProps {
  onClose: () => void;

  onTask: () => void;

  onProcess: () => void;
}

export function TemplateKindDialog({
  onClose,
  onTask,
  onProcess,
}: TemplateKindDialogProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t('tasklib.kind.title')}
      actions={
        <Button variant="quiet" onClick={onClose}>
          {t('tasklib.form.cancel')}
        </Button>
      }
    >
      <p className="fo-kind-lead">{t('tasklib.kind.lead')}</p>

      <div className="fo-kind-choices">
        <button type="button" className="fo-kind-card" onClick={onTask}>
          <strong>{t('tasklib.kind.taskTitle')}</strong>
          <span>{t('tasklib.kind.taskBody')}</span>
          <small>{t('tasklib.kind.taskExample')}</small>
        </button>

        <button type="button" className="fo-kind-card" onClick={onProcess}>
          <strong>{t('tasklib.kind.processTitle')}</strong>
          <span>{t('tasklib.kind.processBody')}</span>
          <small>{t('tasklib.kind.processExample')}</small>
        </button>
      </div>
    </Dialog>
  );
}
