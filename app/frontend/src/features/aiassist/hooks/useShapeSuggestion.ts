import { useQuery, type UseQueryResult } from '@tanstack/react-query';

import { fetchShapeSuggestion, type ShapeSuggestion } from '../api/shapeSuggestionApi';

export function useShapeSuggestion(
  templateId: string,
  wanted: boolean,
): UseQueryResult<ShapeSuggestion, Error> {
  return useQuery({
    queryKey: ['ai-assist', 'template-shape', templateId],
    queryFn: () => fetchShapeSuggestion(templateId),
    enabled: wanted,
    retry: false,
    staleTime: 10 * 60 * 1000,
  });
}
