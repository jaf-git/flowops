import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';
import type { InsightDecision } from '../hooks/useInsightDecision';

interface InsightActionsProps {
  insight: Insight;
  decision: InsightDecision;

  onExplain?: (insight: Insight) => void;

  explaining?: boolean;

  explainFailed?: boolean;
}

export function InsightActions({
  insight,
  decision,
  onExplain,
  explaining = false,
  explainFailed = false,
}: InsightActionsProps): JSX.Element {
  const { t } = useTranslation();

  const deciding = decision.deciding === insight.findingKey;
  const refusal = decision.refusalFor(insight);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-3)',
          flexWrap: 'wrap',
        }}
      >
        {insight.action === null ? null : (
          <button
            type="button"
            className="ui-button ui-button-primary"
            disabled={deciding}
            onClick={() => decision.apply(insight)}
          >
            {t('insight.apply')}
          </button>
        )}

        <button
          type="button"
          className="ui-button ui-button-quiet"
          disabled={deciding}
          onClick={() => decision.dismiss(insight)}
        >
          {t('insight.dismiss')}
        </button>

        {onExplain === undefined ? null : (
          <button
            type="button"
            className="ui-button ui-button-quiet"
            disabled={explaining}
            onClick={() => onExplain(insight)}
          >
            {explaining ? t('insight.explaining') : t('insight.explain')}
          </button>
        )}

        {explainFailed ? (
          <span role="alert" style={{ color: 'var(--alert)', fontSize: 'var(--text-sm)' }}>
            {t('insight.explainFailed')}
          </span>
        ) : null}
      </div>

      {refusal === null ? null : (
        <p
          role="alert"
          style={{ margin: 0, color: 'var(--alert)', fontSize: 'var(--text-sm)', lineHeight: 1.45 }}
        >
          {t(`insight.refusal.${refusal.code}`, { defaultValue: refusal.message })}
        </p>
      )}
    </div>
  );
}
