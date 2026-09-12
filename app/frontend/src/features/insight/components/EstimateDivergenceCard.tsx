import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';
import { EvidenceLine } from './EvidenceLine';
import { InsightActions } from './InsightActions';
import { monthsBetween } from '../model/window';

interface EstimateDivergenceCardProps {
  insight: Insight;

  decision: InsightDecision;
}

function readable(ms: number): string {
  const hours = ms / 3_600_000;
  if (hours < 1) {
    return `${Math.max(1, Math.round(ms / 60_000))}m`;
  }
  return `${Number(hours.toFixed(1))}h`;
}

export function EstimateDivergenceCard({
  insight,
  decision,
}: EstimateDivergenceCardProps): JSX.Element | null {
  const { t } = useTranslation();

  const median = insight.medianWorkMs;
  if (median === null || median === undefined) {
    return null;
  }
  const estimated = insight.estimatedMs;
  const months = monthsBetween(insight.windowFrom, insight.windowTo);

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
        {t('insight.estimateDivergence.eyebrow')}
      </p>

      <p style={{ margin: 0, fontSize: 'var(--text-base)', lineHeight: 1.45 }}>
        {estimated === null || estimated === undefined
          ? t('insight.estimateDivergence.claimNoEstimate', {
              name: insight.subjectName,
              actual: readable(median),
            })
          : t('insight.estimateDivergence.claim', {
              name: insight.subjectName,
              estimate: readable(estimated),
              actual: readable(median),
            })}
      </p>

      {insight.spreadIsWide &&
      insight.fastestMiddleMs !== null &&
      insight.slowestMiddleMs !== null ? (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', lineHeight: 1.45 }}>
          {t('insight.estimateDivergence.spread', {
            fastest: readable(insight.fastestMiddleMs),
            slowest: readable(insight.slowestMiddleMs),
          })}
        </p>
      ) : null}

      <EvidenceLine insight={insight}>
        {t('insight.estimateDivergence.evidence', { count: insight.occurrences, months })}
      </EvidenceLine>

      <InsightActions insight={insight} decision={decision} />
    </article>
  );
}
