import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Dialog } from '../../../shared/ui/Dialog';
import { Field } from '../../../shared/ui/Field';
import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { Textarea } from '../../../shared/ui/Textarea';
import { useDecideDeadline } from '../hooks/useTasks';
import type { DeadlineProposal, Task } from '../api/taskApi';

interface DecideDeadlineDialogProps {
  taskId: string;

  proposal: DeadlineProposal;
  onClose: () => void;
  onDecided: (task: Task) => void;
}

export function DecideDeadlineDialog({
  taskId,
  proposal,
  onClose,
  onDecided,
}: DecideDeadlineDialogProps): JSX.Element {
  const { t } = useTranslation();
  const decide = useDecideDeadline();
  const [accept, setAccept] = useState(true);
  const [reason, setReason] = useState('');

  function close(): void {
    decide.reset();
    onClose();
  }

  function submit(): void {
    decide.mutate(
      { id: taskId, accept, reason: accept ? '' : reason },
      {
        onSuccess: (task) => {
          decide.reset();
          setReason('');
          onDecided(task);
          onClose();
        },
      },
    );
  }

  const code = decide.error instanceof ApiError ? decide.error.code : undefined;

  const owedAnAnswer = !accept && reason.trim() === '';

  return (
    <Dialog
      open
      onCancel={close}
      title={t('task.decideDeadline.title')}
      actions={
        <>
          <Button variant="quiet" onClick={close} data-dialog-cancel>
            {t('task.decideDeadline.cancel')}
          </Button>
          <Button
            onClick={submit}
            disabled={owedAnAnswer}
            loading={decide.isPending}
            loadingLabel={t('task.decideDeadline.submitting')}
          >
            {t('task.decideDeadline.submit')}
          </Button>
        </>
      }
    >
      {code !== undefined && (
        <Banner tone="alert">
          {t(`task.decideDeadline.error.${code}`, t('task.decideDeadline.error.UNKNOWN'))}
        </Banner>
      )}

      <dl className="fo-drawer-fields">
        <dt>{t('task.decideDeadline.asked')}</dt>
        <dd>
          <RelativeTime value={proposal.proposedDeadline} />
        </dd>
        <dt>{t('task.decideDeadline.because')}</dt>
        <dd>{proposal.reason}</dd>
      </dl>

      <fieldset className="fo-task-choice">
        <label className="fo-task-choice-option">
          <input
            type="radio"
            name="deadline-decision"
            checked={accept}
            onChange={() => setAccept(true)}
          />
          {t('task.decideDeadline.accept')}
        </label>
        <label className="fo-task-choice-option">
          <input
            type="radio"
            name="deadline-decision"
            checked={!accept}
            onChange={() => setAccept(false)}
          />
          {t('task.decideDeadline.decline')}
        </label>
      </fieldset>

      {!accept && (
        <Field
          id="decline-reason"
          label={t('task.decideDeadline.field.reason')}
          hint={t('task.decideDeadline.hint.reason')}
          required
        >
          <Textarea
            id="decline-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={2000}
            describedBy="hint"
          />
        </Field>
      )}
    </Dialog>
  );
}
