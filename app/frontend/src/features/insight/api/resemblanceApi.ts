import { apiRequest } from '../../../shared/api/client';

export interface Resemblance {
  templateId: string;
  title: string;

  score: number;

  why: string;
}

export interface ResemblanceAnswer {
  match: Resemblance | null;
}

export function resembling(text: string): Promise<ResemblanceAnswer> {
  return apiRequest<ResemblanceAnswer>('/node-pipeline/resemblance', {
    method: 'POST',
    body: JSON.stringify({ text }),
  });
}
