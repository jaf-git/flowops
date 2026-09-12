import { apiRequest } from '../../../shared/api/client';

export interface ShapeSuggestion {
  available: boolean;
  suggested: boolean;
  steps: SuggestedStep[];
  evidenceLines: number;
  partial: boolean;
}

export interface SuggestedStep {
  sourceKey: string;
  text: string;
}

export function fetchShapeSuggestion(templateId: string): Promise<ShapeSuggestion> {
  return apiRequest<ShapeSuggestion>(`/ai-assist/template-shape/${templateId}`);
}
