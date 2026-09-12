import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';
import { InsightActions } from './InsightActions';

interface BlockPatternCardProps {
  insight: Insight;

  decision: InsightDecision;
  onExplain?: (insight: Insight) => void;
  explaining?: boolean;
  explainFailed?: boolean;
}

export function BlockPatternCard({
  insight,
  decision,
  onExplain,
  explaining = false,
  explainFailed = false,
}: BlockPatternCardProps): JSX.Element {
  const { t } = useTranslation();

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
        {t('insight.blockPattern.eyebrow')}
      </p>

      <p style={{ margin: 0, fontSize: 'var(--text-base)', lineHeight: 1.45 }}>
        <strong>
          {t('insight.blockPattern.claim', {
            step: insight.stepTitle,
            count: insight.occurrences,
          })}
        </strong>
      </p>

      <blockquote
        style={{
          margin: 0,
          paddingLeft: 'var(--space-3)',
          borderLeft: '2px solid var(--faint)',
          color: 'var(--ink)',
          fontSize: 'var(--text-md)',
          fontStyle: 'italic',
        }}
      >
        {insight.reason}
      </blockquote>

      <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
        {t('insight.blockPattern.evidence', { count: insight.runsTotal })}
      </p>

      <InsightActions
        insight={insight}
        decision={decision}
        onExplain={onExplain}
        explaining={explaining}
        explainFailed={explainFailed}
      />
    </article>
  );
}
