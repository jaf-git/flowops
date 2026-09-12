import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { evidenceFor, type Insight, type SubjectType } from '../api/insightApi';
import { useInsightDecision, type InsightDecision } from '../hooks/useInsightDecision';
import { useInsights } from '../hooks/useInsights';
import { offerAsDownload } from '../model/download';
import { BlockPatternCard } from './BlockPatternCard';
import { EstimateDivergenceCard } from './EstimateDivergenceCard';
import { FalseDependencyCard } from './FalseDependencyCard';
import { MissingStepCard } from './MissingStepCard';
import { SlowStepCard } from './SlowStepCard';
import { UnusedTemplateCard } from './UnusedTemplateCard';

interface TemplateInsightsProps {
  templateId: string;

  subjectType?: SubjectType;
}

function Finding({
  insight,
  decision,
  explaining,
  explainFailed,
  onExplain,
}: {
  insight: Insight;
  decision: InsightDecision;
  explaining: boolean;
  explainFailed: boolean;
  onExplain: () => void;
}): JSX.Element | null {
  const shared = { insight, decision, explaining, explainFailed, onExplain };

  switch (insight.kind) {
    case 'MISSING_STEP':
      return <MissingStepCard {...shared} />;
    case 'SLOW_STEP':
      return <SlowStepCard {...shared} />;
    case 'BLOCK_PATTERN':
      return <BlockPatternCard {...shared} />;
    case 'FALSE_DEPENDENCY':
      return <FalseDependencyCard {...shared} />;

    case 'UNUSED_TEMPLATE':
      return <UnusedTemplateCard insight={insight} decision={decision} />;
    case 'ESTIMATE_DIVERGENCE':
      return <EstimateDivergenceCard insight={insight} decision={decision} />;
    default:
      return null;
  }
}

export function TemplateInsights({
  templateId,
  subjectType = 'process_template',
}: TemplateInsightsProps): JSX.Element | null {
  const { t } = useTranslation();
  const insights = useInsights(templateId, subjectType);
  const decision = useInsightDecision(subjectType, templateId);
  const [fetching, setFetching] = useState(false);
  const [failed, setFailed] = useState(false);

  async function explain(): Promise<void> {
    setFetching(true);
    setFailed(false);
    try {
      offerAsDownload(await evidenceFor(templateId), `flowops-evidence-${templateId}.zip`);
    } catch {
      setFailed(true);
    } finally {
      setFetching(false);
    }
  }

  if (insights.isPending || insights.isError || (insights.data?.length ?? 0) === 0) {
    return null;
  }

  return (
    <section
      aria-label={t('insight.section')}
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}
    >
      {insights.data?.map((found) => (
        <Finding
          key={`${found.kind}-${found.findingKey}`}
          insight={found}
          decision={decision}
          explaining={fetching}
          explainFailed={failed}
          onExplain={() => {
            void explain();
          }}
        />
      ))}
    </section>
  );
}
