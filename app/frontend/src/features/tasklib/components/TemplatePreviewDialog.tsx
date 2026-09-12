import { useQuery } from '@tanstack/react-query';
import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useAnnounce } from '../../../shared/notice/useNotices';
import { Button } from '../../../shared/ui/Button';
import { Chip } from '../../../shared/ui/Chip';
import { Dialog } from '../../../shared/ui/Dialog';
import { Spinner } from '../../../shared/ui/Spinner';
import { fetchTemplate } from '../api/taskTemplateApi';
import { useStampTask } from '../hooks/useTaskTemplates';

interface TemplatePreviewDialogProps {
  templateId: string;
  open: boolean;
  onClose: () => void;

  withWhom?: { id: string; name: string } | null;
}

export function TemplatePreviewDialog({
  templateId,
  open,
  onClose,
  withWhom,
}: TemplatePreviewDialogProps): JSX.Element {
  const { t } = useTranslation();
  const announce = useAnnounce();
  const stamp = useStampTask();

  const template = useQuery({
    queryKey: ['tasklib', 'template', templateId],
    queryFn: () => fetchTemplate(templateId),
    enabled: open,

    retry: false,
  });

  return (
    <Dialog
      open={open}
      onCancel={onClose}
      title={template.data?.title ?? t('tasklib.preview.title')}
      actions={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('tasklib.preview.close')}
          </Button>

          {withWhom != null && template.data?.status === 'APPROVED' && (
            <Button
              onClick={() => {
                stamp.mutate(
                  { id: templateId, task: { assigneeId: withWhom.id } },
                  {
                    onSuccess: () => {
                      announce({
                        tone: 'done',
                        message: t('tasklib.preview.assigned', { name: withWhom.name }),
                        detail: template.data?.title,
                      });
                      onClose();
                    },
                  },
                );
              }}
              loading={stamp.isPending}
              loadingLabel={t('tasklib.preview.assigning')}
            >
              {t('tasklib.preview.assignTo', { name: withWhom.name })}
            </Button>
          )}
        </>
      }
    >
      {template.isPending && <Spinner label={t('tasklib.preview.loading')} />}

      {template.isError && <p className="fo-preview-quiet">{t('tasklib.preview.failed')}</p>}

      {stamp.isError && (
        <p className="fo-preview-refused" role="alert">
          {stamp.error.message}
        </p>
      )}

      {template.data !== undefined && (
        <div className="fo-preview">
          {template.data.discoveredByPipeline && (
            <Chip tone="neutral">{t('tasklib.card.fromThePipeline')}</Chip>
          )}

          {template.data.description !== null && template.data.description !== '' && (
            <p className="fo-preview-desc">{template.data.description}</p>
          )}

          {template.data.metadata.responsibleRole !== null && (
            <p className="fo-preview-role">
              {t('tasklib.preview.responsible', { role: template.data.metadata.responsibleRole })}
            </p>
          )}

          {template.data.checklist.length > 0 && (
            <>
              <p className="fo-preview-eyebrow">
                {t('tasklib.preview.steps', { count: template.data.checklist.length })}
              </p>
              <ol className="fo-preview-steps">
                {template.data.checklist.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
            </>
          )}
        </div>
      )}
    </Dialog>
  );
}
