import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { renderableLink } from '../../../shared/lib/externalLink';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { PhaseBreakdown, type Phase, type PhaseSpan } from '../../../shared/ui/PhaseBreakdown';
import { Select } from '../../../shared/ui/Select';
import { Spinner } from '../../../shared/ui/Spinner';
import { Textarea } from '../../../shared/ui/Textarea';
import { useApproveTask, useReturnTaskForRework, useTaskDetail } from '../hooks/useTasks';
import type { PhaseKind } from '../api/taskApi';

interface TaskReviewPanelProps {
  taskId: string;
  onClose: () => void;
  onApproved: () => void;
  onReturned: () => void;
}

const SCORES = ['1', '2', '3', '4', '5'] as const;

const PHASE_NAMES: Record<PhaseKind, Phase> = {
  WAIT: 'wait',
  ACTIVE: 'active',
  BLOCKED: 'blocked',
  REVIEW: 'review',
  APPROVAL: 'approval',
};

export function TaskReviewPanel({
  taskId,
  onClose,
  onApproved,
  onReturned,
}: TaskReviewPanelProps): JSX.Element {
  const { t } = useTranslation();
  const detail = useTaskDetail(taskId);
  const approve = useApproveTask();
  const sendBack = useReturnTaskForRework();
  const [score, setScore] = useState('4');
  const [comment, setComment] = useState('');
  const [reason, setReason] = useState('');
  const [returning, setReturning] = useState(false);

  const failure = approve.error ?? sendBack.error;
  const code = failure instanceof ApiError ? failure.code : undefined;
  const nothingToActOn = reason.trim() === '';
  const busy = approve.isPending || sendBack.isPending;

  function decide(): void {
    if (returning) {
      sendBack.mutate(
        { id: taskId, reason },
        {
          onSuccess: () => {
            onReturned();
            onClose();
          },
        },
      );
      return;
    }
    approve.mutate(
      { id: taskId, score: Number(score), comment },
      {
        onSuccess: () => {
          onApproved();
          onClose();
        },
      },
    );
  }

  const spans: PhaseSpan[] = (detail.data?.phases ?? []).map((span) => ({
    phase: PHASE_NAMES[span.kind],
    seconds: span.seconds,
  }));
  const link = renderableLink(detail.data?.proof?.externalLink);

  return (
    <Dialog
      open
      onCancel={onClose}
      title={t('task.review.title')}
      actions={
        <>
          <Button variant="quiet" onClick={onClose} data-dialog-cancel>
            {t('task.review.cancel')}
          </Button>
          {returning ? (
            <Button
              onClick={decide}
              disabled={nothingToActOn}
              loading={sendBack.isPending}
              loadingLabel={t('task.review.returning')}
            >
              {t('task.review.return')}
            </Button>
          ) : (
            <Button
              onClick={decide}
              loading={approve.isPending}
              loadingLabel={t('task.review.approving')}
            >
              {t('task.review.approve')}
            </Button>
          )}
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.review.error.${code}`, t('task.review.error.UNKNOWN'))}
        </Banner>
      )}

      {detail.isPending && <Spinner label={t('task.review.loading')} />}
      {detail.isError && <Banner tone="alert">{t('task.review.loadFailed')}</Banner>}

      {detail.data !== undefined && (
        <div style={{ display: 'grid', gap: 'var(--gap-4)' }}>
          <section>
            <h3 style={{ margin: 0, fontSize: 'var(--text-body)' }}>
              {t('task.review.delivered')}
            </h3>
            <p style={{ margin: 'var(--gap-2) 0 0', whiteSpace: 'pre-wrap' }}>
              {detail.data.proof?.note ?? t('task.review.noProof')}
            </p>
            {detail.data.proof?.externalLink != null &&
              (link === undefined ? (
                <p style={{ margin: 'var(--gap-2) 0 0', color: 'var(--ink-500)' }}>
                  {t('task.review.linkNotFollowable', { link: detail.data.proof.externalLink })}
                </p>
              ) : (
                <p style={{ margin: 'var(--gap-2) 0 0' }}>
                  <a href={link} target="_blank" rel="noopener noreferrer">
                    {link}
                  </a>
                </p>
              ))}
          </section>

          <section>
            <h3 style={{ margin: 0, fontSize: 'var(--text-body)' }}>{t('task.review.deadline')}</h3>
            <p style={{ margin: 'var(--gap-2) 0 0' }}>
              {detail.data.deadlineMet === null
                ? t('task.review.notSubmitted')
                : detail.data.deadlineMet
                  ? t('task.review.onTime')
                  : t('task.review.late')}
            </p>
          </section>

          <section>
            <h3 style={{ margin: 0, fontSize: 'var(--text-body)' }}>{t('task.review.time')}</h3>
            <PhaseBreakdown spans={spans} />
          </section>

          {returning ? (
            <Field
              id="review-reason"
              label={t('task.review.field.reason')}
              hint={t('task.review.hint.reason')}
              required
            >
              <Textarea
                id="review-reason"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={4000}
                rows={4}
                describedBy="hint"
              />
            </Field>
          ) : (
            <>
              <Field
                id="review-score"
                label={t('task.review.field.score')}
                hint={t('task.review.hint.score')}
                required
              >
                <Select
                  id="review-score"
                  value={score}
                  onChange={(event) => setScore(event.target.value)}
                  options={SCORES.map((option) => ({
                    value: option,
                    label: t(`task.review.score.${option}`),
                  }))}
                  describedBy="hint"
                />
              </Field>
              <Field
                id="review-comment"
                label={t('task.review.field.comment')}
                hint={t('task.review.hint.comment')}
              >
                <Textarea
                  id="review-comment"
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                  maxLength={4000}
                  rows={3}
                  describedBy="hint"
                />
              </Field>
            </>
          )}

          <Button variant="quiet" onClick={() => setReturning(!returning)} disabled={busy}>
            {returning ? t('task.review.switchToApprove') : t('task.review.switchToReturn')}
          </Button>
        </div>
      )}
    </Dialog>
  );
}
