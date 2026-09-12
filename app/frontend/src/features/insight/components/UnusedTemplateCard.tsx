import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';
import { InsightActions } from './InsightActions';

interface UnusedTemplateCardProps {
  insight: Insight;

  decision: InsightDecision;
}

export function UnusedTemplateCard({ insight, decision }: UnusedTemplateCardProps): JSX.Element {
  const { t } = useTranslation();

  const days = insight.daysSinceLastUse ?? -1;
  const everUsed = days >= 0;

  return (
    <article
      className="ui-card"
      style={{
        borderLeft: '3px solid var(--brand)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)',
      }}
    >
      <p className="fo-eyebrow" style={{ color: 'var(--brand-dark)' }}>
        {t('insight.unusedTemplate.eyebrow')}
      </p>

      <p style={{ margin: 0, fontSize: 'var(--text-base)', lineHeight: 1.45 }}>
        {everUsed
          ? t('insight.unusedTemplate.claim', { name: insight.subjectName, count: days })
          : t('insight.unusedTemplate.claimNeverUsed', { name: insight.subjectName })}
      </p>

      <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
        {insight.expectedIntervalDays === null || insight.expectedIntervalDays === undefined
          ? t('insight.unusedTemplate.evidence', {
              count: insight.occurrences,
              window: insight.windowDays ?? 90,
            })
          : t('insight.unusedTemplate.evidenceByRhythm', { count: insight.occurrences })}
      </p>

      {insight.expectedIntervalDays !== null && insight.expectedIntervalDays !== undefined ? (
        <p
          style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)', lineHeight: 1.45 }}
        >
          {t('insight.unusedTemplate.rhythm', { count: insight.expectedIntervalDays })}
        </p>
      ) : (
        <p
          style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)', lineHeight: 1.45 }}
        >
          {t('insight.unusedTemplate.seasonal')}
        </p>
      )}

      <InsightActions insight={insight} decision={decision} />
    </article>
  );
}
