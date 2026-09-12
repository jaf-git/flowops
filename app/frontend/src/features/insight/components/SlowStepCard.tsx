import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { PhaseBreakdown, type PhaseSpan } from '../../../shared/ui/PhaseBreakdown';
import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';
import { EvidenceLine } from './EvidenceLine';
import { InsightActions } from './InsightActions';
import { monthsBetween } from '../model/window';

interface SlowStepCardProps {
  insight: Insight;

  decision: InsightDecision;
  onExplain?: (insight: Insight) => void;
  explaining?: boolean;
  explainFailed?: boolean;
}

export function SlowStepCard({
  insight,
  decision,
  onExplain,
  explaining = false,
  explainFailed = false,
}: SlowStepCardProps): JSX.Element {
  const { t } = useTranslation();

  const months = monthsBetween(insight.windowFrom, insight.windowTo);
  const phases = insight.medianPhases;

  const spans: PhaseSpan[] =
    phases === null
      ? []
      : [
          { phase: 'active', seconds: Math.round(phases.workMs / 1000) },
          { phase: 'blocked', seconds: Math.round(phases.blockedMs / 1000) },
          { phase: 'wait', seconds: Math.round(phases.waitingMs / 1000) },
          { phase: 'review', seconds: Math.round(phases.reviewMs / 1000) },
        ];

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
        {t('insight.slowStep.eyebrow')}
      </p>

      <p style={{ margin: 0, fontSize: 'var(--text-base)', lineHeight: 1.45 }}>
        <strong>{t('insight.slowStep.claim', { step: insight.stepTitle })}</strong>
      </p>

      <PhaseBreakdown spans={spans} />

      <EvidenceLine insight={insight}>
        {t('insight.slowStep.evidence', { count: insight.runsTotal, months })}
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
