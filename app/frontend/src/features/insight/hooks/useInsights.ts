import { useQuery, type UseQueryResult } from '@tanstack/react-query';

import { insightsFor, type Insight, type SubjectType } from '../api/insightApi';

export function insightsKey(subjectType: SubjectType, subjectId: string | null): unknown[] {
  return ['insights', subjectType, subjectId];
}

export function useInsights(
  subjectId: string | null,
  subjectType: SubjectType = 'process_template',
): UseQueryResult<Insight[]> {
  return useQuery({
    queryKey: insightsKey(subjectType, subjectId),
    queryFn: () => insightsFor(subjectType, subjectId as string),
    enabled: subjectId !== null,
    staleTime: 60_000,
  });
}
