import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Avatar } from '../../../shared/ui/Avatar';
import { durationLabel } from '../../../shared/lib/elapsed';
import type { CardTip } from '../model/tip';

export function StepCardTip({ tip }: { tip: CardTip | null }): JSX.Element | null {
  const { t, i18n } = useTranslation();

  if (tip === null) {
    return null;
  }

  return (
    <div
      className="fo-card-tip"
      role="tooltip"
      data-below={tip.below ? 'true' : undefined}
      style={{ left: `${String(tip.x)}px`, top: `${String(tip.y)}px` }}
    >
      <p
        style={{
          margin: 0,
          fontSize: 'var(--text-sm)',
          fontWeight: 600,
          color: 'var(--ink)',
          lineHeight: 1.35,
        }}
      >
        {tip.card.title}
      </p>
      <p
        style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--text-xs)', color: 'var(--muted)' }}
      >
        {t(`canvas.board.state.${tip.card.state}`)}

        {tip.card.bottleneckMinutes === null
          ? ''
          : ` · ${t('canvas.board.waited', {
              duration: durationLabel(tip.card.bottleneckMinutes * 60, i18n.language),
            })}`}
      </p>

      {tip.card.blockedReason === null ? null : (
        <p
          style={{
            margin: 'var(--space-2) 0 0',
            fontSize: 'var(--text-xs)',
            color: 'var(--alert)',
            lineHeight: 1.5,
          }}
        >
          {tip.card.blockedReason}
        </p>
      )}

      {tip.card.description === null || tip.card.description === '' ? null : (
        <p className="fo-card-tip-desc">{tip.card.description}</p>
      )}

      {tip.card.assigneeId === null && tip.card.deadline === null ? null : (
        <dl className="fo-card-tip-fields">
          {tip.card.assigneeId === null ? null : (
            <>
              <dt>{t('canvas.tip.assignee')}</dt>
              <dd
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)',
                  justifyContent: 'flex-end',
                }}
              >
                <Avatar id={tip.card.assigneeId} name={tip.card.assigneeName ?? ''} size={18} />
                {tip.card.assigneeName === null || tip.card.assigneeName === ''
                  ? t('task.formerMember')
                  : tip.card.assigneeName}
              </dd>
            </>
          )}
          {tip.card.deadline === null ? null : (
            <>
              <dt>{t('canvas.tip.deadline')}</dt>
              <dd style={{ color: tip.card.atRisk ? 'var(--waiting)' : undefined }}>
                {new Date(tip.card.deadline).toLocaleDateString()}
                {tip.card.atRisk ? ` · ${t('task.atRisk')}` : ''}
              </dd>
            </>
          )}
        </dl>
      )}
    </div>
  );
}
