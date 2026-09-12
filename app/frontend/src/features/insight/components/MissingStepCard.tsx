import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';
import { EvidenceLine } from './EvidenceLine';
import { InsightActions } from './InsightActions';
import { monthsBetween } from '../model/window';

interface MissingStepCardProps {
  insight: Insight;

  decision: InsightDecision;

  onExplain?: (insight: Insight) => void;

  explaining?: boolean;

  explainFailed?: boolean;
}

export function MissingStepCard({
  insight,
  decision,
  onExplain,
  explaining = false,
  explainFailed = false,
}: MissingStepCardProps): JSX.Element {
  const { t } = useTranslation();

  const months = monthsBetween(insight.windowFrom, insight.windowTo);
  const where = insight.afterStep
    ? t('insight.missingStep.after', { step: insight.afterStep })
    : t('insight.missingStep.atTheStart');

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
        {t('insight.missingStep.eyebrow')}
      </p>

      <p style={{ margin: 0, fontSize: 'var(--text-base)', lineHeight: 1.45 }}>
        <strong>
          {t('insight.missingStep.claim', {
            added: insight.occurrences,
            count: insight.runsTotal,
            step: insight.stepTitle,
          })}
        </strong>{' '}
        {where}
      </p>

      <EvidenceLine insight={insight}>
        {t('insight.missingStep.evidence', { count: insight.runsTotal, months })}
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
