import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { Insight } from '../api/insightApi';

interface EvidenceLineProps {
  insight: Insight;

  children: React.ReactNode;
}

export function EvidenceLine({ insight, children }: EvidenceLineProps): JSX.Element {
  const { t } = useTranslation();
  const excluded = insight.excludedRuns;

  return (
    <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)', lineHeight: 1.45 }}>
      {children}
      {excluded !== null && excluded !== undefined ? (
        <>
          {' '}
          <span data-testid="closure-coverage">
            {t('insight.evidence.neverClosed', { count: excluded })}
          </span>
        </>
      ) : null}
    </p>
  );
}
