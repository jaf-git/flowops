import { apiRequest } from '../../../shared/api/client';
import type { WorkDraft } from '../../../shared/model/work-draft';

export type {
  AgreedWork,
  FieldSource,
  Sourced,
  SuggestedStep,
  WorkDraft,
  WorkShape,
} from '../../../shared/model/work-draft';

export interface WorkSuggestion extends WorkDraft {
  available: boolean;
}

export function suggestWorkIn(conversationId: string): Promise<WorkSuggestion> {
  return apiRequest<WorkSuggestion>(
    `/chat-assist/conversations/${encodeURIComponent(conversationId)}/work-suggestion`,
    { method: 'POST' },
  );
}

export interface NewProcessStep {
  title: string;
  description: string | null;
  assigneeId: string;
  deadline: string | null;
  priority: string;
}

export function startProcessFromDescriptions(
  name: string,
  processOwnerId: string,
  steps: readonly NewProcessStep[],
): Promise<{ id: string }> {
  return apiRequest<{ id: string }>('/process-instances/from-descriptions', {
    method: 'POST',
    body: JSON.stringify({ name, processOwnerId, steps }),
  });
}
