import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { useAcknowledgeDeadlineNotice, useDeadlineNotices } from '../hooks/useTasks';

interface DeadlineNoticeStripProps {
  onChange: (taskId: string) => void;
}

export function DeadlineNoticeStrip({ onChange }: DeadlineNoticeStripProps): JSX.Element | null {
  const { t } = useTranslation();
  const notices = useDeadlineNotices();
  const acknowledge = useAcknowledgeDeadlineNotice();

  const rows = notices.data?.notices ?? [];
  if (rows.length === 0) {
    return null;
  }

  return (
    <section aria-label={t('task.setDeadline.noticesHeading')} className="ui-card fo-task-notices">
      <h2 className="fo-eyebrow fo-task-notices-heading">{t('task.setDeadline.noticesHeading')}</h2>

      {rows.map((notice) => (
        <div key={notice.taskId} className="fo-task-notice">
          <span className="fo-task-notice-line">
            {notice.assigneeName === '' ? t('task.formerMember') : notice.assigneeName} ·{' '}
            {notice.taskTitle} → {new Date(notice.deadline).toLocaleDateString()}
          </span>

          <Button
            variant="secondary"
            onClick={() => acknowledge.mutate(notice.taskId)}
            loading={acknowledge.isPending}
            loadingLabel={t('task.setDeadline.submitting')}
          >
            {t('task.setDeadline.acknowledge')}
          </Button>

          <Button variant="quiet" onClick={() => onChange(notice.taskId)}>
            {t('task.setDeadline.change')}
          </Button>
        </div>
      ))}

      {acknowledge.isError && (
        <Banner tone="alert">{t('task.setDeadline.acknowledgeFailed')}</Banner>
      )}
    </section>
  );
}
