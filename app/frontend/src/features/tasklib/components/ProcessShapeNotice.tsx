import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import type { ProcessShapeHint } from '../api/taskTemplateApi';

interface ProcessShapeNoticeProps {
  hints: readonly ProcessShapeHint[];

  onConvert?: () => void;
  onDismiss?: () => void;
}

export function ProcessShapeNotice({
  hints,
  onConvert,
  onDismiss,
}: ProcessShapeNoticeProps): JSX.Element | null {
  const { t } = useTranslation();

  if (hints.length === 0) {
    return null;
  }

  return (
    <aside className="fo-shape-notice" aria-live="polite">
      <p className="fo-shape-notice-lead">{t('tasklib.shape.lead')}</p>
      <ul className="fo-shape-notice-list">
        {hints.map((hint) => (
          <li key={`${hint.signal}-${hint.evidence}`}>
            {t(`tasklib.shape.${hint.signal}`, { evidence: hint.evidence })}
          </li>
        ))}
      </ul>
      <div className="fo-shape-notice-actions">
        {onConvert !== undefined && (
          <Button variant="secondary" onClick={onConvert}>
            {t('tasklib.shape.convert')}
          </Button>
        )}
        {onDismiss !== undefined && (
          <Button variant="quiet" onClick={onDismiss}>
            {t('tasklib.shape.dismiss')}
          </Button>
        )}
      </div>
    </aside>
  );
}
