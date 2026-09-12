import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';
import { EvidenceLine } from './EvidenceLine';
import { InsightActions } from './InsightActions';

interface FalseDependencyCardProps {
  insight: Insight;

  decision: InsightDecision;
  onExplain?: (insight: Insight) => void;
  explaining?: boolean;
  explainFailed?: boolean;
}

export function FalseDependencyCard({
  insight,
  decision,
  onExplain,
  explaining = false,
  explainFailed = false,
}: FalseDependencyCardProps): JSX.Element {
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
        {t('insight.falseDependency.eyebrow')}
      </p>

      <p style={{ margin: 0, fontSize: 'var(--text-base)', lineHeight: 1.45 }}>
        <strong>
          {t('insight.falseDependency.claim', {
            dependent: insight.stepTitle,
            dependsOn: insight.dependsOnStep,
            count: insight.occurrences,
          })}
        </strong>
      </p>

      <p style={{ margin: 0, color: 'var(--slate)', fontSize: 'var(--text-md)', lineHeight: 1.45 }}>
        {t('insight.falseDependency.consider')}
      </p>

      <EvidenceLine insight={insight}>
        {t('insight.falseDependency.evidence', { count: insight.runsTotal })}
      </EvidenceLine>

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
