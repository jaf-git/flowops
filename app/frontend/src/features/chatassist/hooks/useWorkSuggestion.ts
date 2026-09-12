import { useMutation, type UseMutationResult } from '@tanstack/react-query';

import type { ApiError } from '../../../shared/api/client';
import { suggestWorkIn, type WorkSuggestion } from '../api/chatAssistApi';

export function useWorkSuggestion(
  conversationId: string | null,
): UseMutationResult<WorkSuggestion, ApiError, void> {
  return useMutation<WorkSuggestion, ApiError, void>({
    mutationFn: () => suggestWorkIn(conversationId as string),

    retry: false,
  });
}
