import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import type { TaskTemplate, TemplateDraft } from '../api/taskTemplateApi';
import { useShapeCheck } from '../hooks/useTaskTemplates';
import { useTemplateDraft } from '../hooks/useTemplateDraft';
import { ProcessShapeNotice } from './ProcessShapeNotice';
import { TemplateFieldset } from './TemplateFieldset';

interface TemplateFormDialogProps {
  editing?: TaskTemplate;
  knownTypes: readonly string[];
  onClose: () => void;
  onSubmit: (draft: TemplateDraft) => void;

  busy: boolean;
}

export function TemplateFormDialog({
  editing,
  knownTypes,
  onClose,
  onSubmit,
  busy,
}: TemplateFormDialogProps): JSX.Element {
  const { t } = useTranslation();
  const draft = useTemplateDraft(editing);
  const shape = useShapeCheck({
    title: draft.values.title,
    checklist: draft.values.checklist,
    enabled: true,
  });

  return (
    <Dialog
      open
      onCancel={onClose}
      title={editing === undefined ? t('tasklib.form.newTitle') : t('tasklib.form.editTitle')}
      actions={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            {t('tasklib.form.cancel')}
          </Button>
          <Button
            variant="secondary"
            onClick={() => onSubmit(draft.toDraft(false))}
            disabled={busy || !draft.isComplete}
          >
            {t('tasklib.form.saveDraft')}
          </Button>
          <Button
            variant="primary"
            onClick={() => onSubmit(draft.toDraft(true))}
            disabled={busy || !draft.isComplete}
          >
            {t('tasklib.form.submit')}
          </Button>
        </>
      }
    >
      {editing?.rejectionReason !== null && editing?.rejectionReason !== undefined && (
        <p className="fo-template-sent-back">
          {t('tasklib.form.sentBackBecause', { reason: editing.rejectionReason })}
        </p>
      )}
      <TemplateFieldset
        draft={draft}
        knownTypes={knownTypes}
        idPrefix="template"
        showGroupHeadings
      />

      <ProcessShapeNotice hints={shape.data ?? []} />
    </Dialog>
  );
}
