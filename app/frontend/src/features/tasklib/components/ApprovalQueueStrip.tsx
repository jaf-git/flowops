import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Checkbox } from '../../../shared/ui/Checkbox';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { RelativeTime } from '../../../shared/ui/RelativeTime';
import type { TaskTemplate, TemplateDraft } from '../api/taskTemplateApi';
import { useShapeCheck } from '../hooks/useTaskTemplates';
import { useTemplateDraft } from '../hooks/useTemplateDraft';
import { ProcessShapeNotice } from './ProcessShapeNotice';
import { TemplateFieldset } from './TemplateFieldset';

interface ApprovalQueueStripProps {
  queue: readonly TaskTemplate[];
  knownTypes: readonly string[];
  onApprove: (id: string, edited?: TemplateDraft) => void;
  onApproveMany: (ids: string[]) => void;
  onSendBack: (id: string, reason: string) => void;
  busy: boolean;
}

export function ApprovalQueueStrip({
  queue,
  knownTypes,
  onApprove,
  onApproveMany,
  onSendBack,
  busy,
}: ApprovalQueueStripProps): JSX.Element | null {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<readonly string[]>([]);
  const [expanded, setExpanded] = useState<string | undefined>(undefined);

  if (queue.length === 0) {
    return null;
  }

  const live = selected.filter((id) => queue.some((template) => template.id === id));

  return (
    <section className="fo-approval-strip" aria-label={t('tasklib.queue.heading')}>
      <header className="fo-approval-head">
        <h3 className="fo-approval-title">{t('tasklib.queue.heading')}</h3>
        <span className="fo-approval-count">
          {t('tasklib.queue.count', { count: queue.length })}
        </span>
        {live.length > 0 && (
          <Button
            variant="primary"
            onClick={() => {
              onApproveMany([...live]);
              setSelected([]);
            }}
            disabled={busy}
          >
            {t('tasklib.queue.approveSelected', { count: live.length })}
          </Button>
        )}
      </header>

      <ul className="fo-approval-list">
        {queue.map((template) => (
          <li key={template.id} className="fo-approval-row" data-open={expanded === template.id}>
            <div className="fo-approval-summary">
              <Checkbox
                id={`approve-${template.id}`}
                label={template.title}
                checked={live.includes(template.id)}
                onChange={() =>
                  setSelected((current) =>
                    current.includes(template.id)
                      ? current.filter((entry) => entry !== template.id)
                      : [...current, template.id],
                  )
                }
              />
              {template.type !== null && template.type !== '' && (
                <span className="fo-template-type">{template.type}</span>
              )}
              <span className="fo-approval-when">
                <RelativeTime value={template.createdAt} />
              </span>
              <Button
                variant="quiet"
                aria-expanded={expanded === template.id}
                onClick={() => setExpanded(expanded === template.id ? undefined : template.id)}
              >
                {expanded === template.id ? t('tasklib.queue.collapse') : t('tasklib.queue.review')}
              </Button>
            </div>

            {expanded === template.id && (
              <ProposalReview
                key={template.id}
                template={template}
                knownTypes={knownTypes}
                busy={busy}
                onApprove={onApprove}
                onSendBack={onSendBack}
              />
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function ProposalReview({
  template,
  knownTypes,
  busy,
  onApprove,
  onSendBack,
}: {
  template: TaskTemplate;
  knownTypes: readonly string[];
  busy: boolean;
  onApprove: (id: string, edited?: TemplateDraft) => void;
  onSendBack: (id: string, reason: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const draft = useTemplateDraft(template);
  const [reason, setReason] = useState<string | undefined>(undefined);
  const shape = useShapeCheck({
    title: draft.values.title,
    checklist: draft.values.checklist,
    enabled: true,
  });

  return (
    <div className="fo-approval-detail">
      <TemplateFieldset draft={draft} knownTypes={knownTypes} idPrefix={`review-${template.id}`} />

      <ProcessShapeNotice hints={shape.data ?? []} />

      {reason === undefined ? (
        <div className="fo-approval-actions">
          <Button
            variant="primary"
            onClick={() => onApprove(template.id, draft.toDraft(true))}
            disabled={busy || !draft.isComplete}
          >
            {t('tasklib.queue.approve')}
          </Button>
          <Button variant="destructive" onClick={() => setReason('')} disabled={busy}>
            {t('tasklib.queue.reject')}
          </Button>
        </div>
      ) : (
        <div className="fo-approval-sendback">
          <Field
            id={`reason-${template.id}`}
            label={t('tasklib.queue.reasonLabel')}
            hint={t('tasklib.queue.reasonHint')}
          >
            <Input
              id={`reason-${template.id}`}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder={t('tasklib.queue.reasonPlaceholder')}
            />
          </Field>
          <div className="fo-approval-actions">
            <Button
              variant="destructive"
              onClick={() => onSendBack(template.id, reason.trim())}
              disabled={busy || reason.trim() === ''}
            >
              {t('tasklib.queue.confirmSendBack')}
            </Button>
            <Button variant="quiet" onClick={() => setReason(undefined)} disabled={busy}>
              {t('tasklib.form.cancel')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
