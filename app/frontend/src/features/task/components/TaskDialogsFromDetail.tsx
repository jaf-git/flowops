import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';
import type { Task } from '../api/taskApi';
import { useTaskDetail } from '../hooks/useTasks';
import { DecideDeadlineDialog } from './DecideDeadlineDialog';
import { EditTaskDialog } from './EditTaskDialog';

export function DecideDeadlineDialogFromDetail({
  taskId,
  onClose,
  onDecided,
}: {
  taskId: string;
  onClose: () => void;
  onDecided: (task: Task) => void;
}): JSX.Element | null {
  const { t } = useTranslation();
  const detail = useTaskDetail(taskId);

  if (detail.isPending) {
    return null;
  }

  if (detail.isError) {
    return <Banner tone="alert">{t('task.loadFailed')}</Banner>;
  }
  if (detail.data?.deadlineProposal == null) {
    return <Banner tone="alert">{t('task.decideDeadline.error.NO_OPEN_PROPOSAL')}</Banner>;
  }
  return (
    <DecideDeadlineDialog
      taskId={taskId}
      proposal={detail.data.deadlineProposal}
      onClose={onClose}
      onDecided={onDecided}
    />
  );
}

export function EditTaskDialogFromDetail({
  taskId,
  onClose,
  onEdited,
}: {
  taskId: string;
  onClose: () => void;
  onEdited: (task: Task) => void;
}): JSX.Element | null {
  const { t } = useTranslation();
  const detail = useTaskDetail(taskId);

  if (detail.isPending) {
    return null;
  }

  if (detail.isError || detail.data === undefined) {
    return <Banner tone="alert">{t('task.loadFailed')}</Banner>;
  }
  return <EditTaskDialog task={detail.data} onClose={onClose} onEdited={onEdited} />;
}
