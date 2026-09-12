import { useMutation, useQueryClient } from '@tanstack/react-query';

import { ApiError } from '../../../shared/api/client';
import { applyInsight, dismissInsight, type Insight, type SubjectType } from '../api/insightApi';
import { insightsKey } from './useInsights';

export interface InsightDecision {
  apply: (insight: Insight) => void;
  dismiss: (insight: Insight) => void;

  deciding: string | null;

  refusalFor: (insight: Insight) => Refusal | null;
}

export interface Refusal {
  code: string;
  message: string;
}

export function useInsightDecision(
  subjectType: SubjectType,
  subjectId: string | null,
): InsightDecision {
  const queries = useQueryClient();

  const recompute = (): void => {
    void queries.invalidateQueries({ queryKey: insightsKey(subjectType, subjectId) });
  };

  const applying = useMutation<void, ApiError, Insight>({
    mutationFn: (insight) => applyInsight(insight, subjectType),
    onSuccess: recompute,
  });

  const dismissing = useMutation<void, ApiError, Insight>({
    mutationFn: (insight) => dismissInsight(insight, subjectType),
    onSuccess: recompute,
  });

  const inFlight = [applying, dismissing].find((decision) => decision.isPending);
  const refused = [applying, dismissing].find((decision) => decision.error !== null);

  return {
    apply: (insight) => applying.mutate(insight),
    dismiss: (insight) => dismissing.mutate(insight),
    deciding: inFlight?.variables?.findingKey ?? null,
    refusalFor: (insight) =>
      refused !== undefined && refused.variables?.findingKey === insight.findingKey
        ? { code: refused.error.code, message: refused.error.message }
        : null,
  };
}
