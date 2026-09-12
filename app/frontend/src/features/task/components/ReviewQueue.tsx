import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { DeadlineIndicator } from '../../../shared/ui/DeadlineIndicator';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { Spinner } from '../../../shared/ui/Spinner';
import { Table } from '../../../shared/ui/Table';
import { useReviewQueue } from '../hooks/useTasks';
import type { TaskSummary } from '../api/taskApi';

interface ReviewQueueProps {
  permissions: readonly string[];
  onOpen: (taskId: string) => void;
}

export function ReviewQueue({ permissions, onOpen }: ReviewQueueProps): JSX.Element | null {
  const { t } = useTranslation();
  const mayReview = permissions.includes('TASK_REVIEW');
  const queue = useReviewQueue(mayReview);

  if (!mayReview) {
    return null;
  }

  if (queue.isPending) {
    return <Spinner label={t('task.reviewQueue.loading')} />;
  }

  if (queue.isError) {
    return <Banner tone="alert">{t('task.reviewQueue.loadFailed')}</Banner>;
  }

  const rows = queue.data?.tasks ?? [];

  return (
    <section className="fo-task-section" aria-labelledby="review-queue">
      <header className="fo-task-section-head">
        <h2 id="review-queue" className="fo-task-section-title">
          {t('task.reviewQueue.heading')}
        </h2>
      </header>

      <Card pad={0}>
        <Table<TaskSummary>
          caption={t('task.reviewQueue.caption')}
          columns={[
            { key: 'title', header: t('task.table.title'), cell: (row) => row.title },
            {
              key: 'assignee',
              header: t('task.table.assignee'),
              cell: (row) => (row.assigneeName === '' ? t('task.formerMember') : row.assigneeName),
            },
            {
              key: 'deadline',
              header: t('task.table.deadline'),
              cell: (row) => <DeadlineIndicator dueAt={row.deadline} />,
            },
            {
              key: 'waiting',
              header: t('task.reviewQueue.waiting'),

              cell: (row) =>
                row.openPhase === null || row.phaseSince === null ? (
                  <span>{t('task.phase.none')}</span>
                ) : (
                  <span>
                    {t(`task.phase.${row.openPhase}`)} <RelativeTime value={row.phaseSince} />
                  </span>
                ),
            },
            {
              key: 'action',
              header: t('task.table.action'),
              cell: (row) => (
                <Button variant="quiet" onClick={() => onOpen(row.id)}>
                  {t('task.reviewQueue.open')}
                </Button>
              ),
            },
          ]}
          rows={rows}
          rowKey={(row) => row.id}
          empty={
            <EmptyState
              icon="check"
              heading={t('task.reviewQueue.empty.heading')}
              body={t('task.reviewQueue.empty.body')}
            />
          }
        />
      </Card>
    </section>
  );
}
