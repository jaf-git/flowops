import { apiRequest } from '../../../shared/api/client';

export type Stage = 'OBSERVE' | 'NORMALISE' | 'MEASURE' | 'DETECT' | 'CORRELATE' | 'RECOMMEND';

export const STAGES: readonly Stage[] = [
  'OBSERVE',
  'NORMALISE',
  'MEASURE',
  'DETECT',
  'CORRELATE',
  'RECOMMEND',
];

export interface AnalysisRun {
  id: string;
  from: string;
  to: string;
  bracketsRead: number;
  findings: number;
  recommendations: number;

  reached: Stage;
}

export interface Finding {
  id: string;
  runId: string;
  detector: string;
  stage: Stage;
  subjectKind: 'WORK_TYPE' | 'CLIENT' | 'ROLE_PAIR' | 'ADDRESS' | 'SHAPE' | 'JOB' | 'WORKSPACE';
  subjectKey: string;
  headline: string;
  phrasing: string | null;

  phrasedBy: 'TEMPLATE' | 'MODEL' | null;

  sampleSize: number;
  measure: number | null;
  unit: string | null;
  createdAt: string;
}

export interface Recommendation {
  id: string;
  runId: string;

  findingId: string;
  kind:
    | 'WRITE_IT_DOWN'
    | 'MOVE_STEP_EARLIER'
    | 'SPLIT_WORK_TYPE'
    | 'CHANGE_WINDOW'
    | 'ASK_FOR_A_SLOT'
    | 'WORK_HAS_NO_OWNER';
  headline: string;
  detail: string | null;

  confidence: 'STRONG' | 'WORTH_LOOKING' | 'UNDERMINED';
  sampleSize: number;
  createdAt: string;
}

export interface StoredRun {
  id: string;
  from: string;
  to: string;
  bracketsRead: number;
  waitsRead: number;
  reached: Stage;
  startedAt: string;
  finishedAt: string | null;
}

export async function fetchLatestRun(): Promise<StoredRun | null> {
  return apiRequest<StoredRun | null>('/discovery/analysis/run/latest').catch(() => null);
}

export function fetchRecommendations(): Promise<Recommendation[]> {
  return apiRequest<Recommendation[]>('/discovery/analysis/recommendations');
}

export interface WrittenDown {
  recommendationId: string;
  processTemplateId: string;
  name: string;
  steps: string[];
}

export function writeItDown(recommendationId: string, name: string): Promise<WrittenDown> {
  return apiRequest<WrittenDown>(
    `/discovery/analysis/recommendations/${encodeURIComponent(recommendationId)}/write-it-down`,
    { method: 'POST', body: JSON.stringify({ name }) },
  );
}

export function dismissRecommendation(recommendationId: string): Promise<void> {
  return apiRequest<void>(
    `/discovery/analysis/recommendations/${encodeURIComponent(recommendationId)}/dismiss`,
    { method: 'POST' },
  );
}

export function shapeInWords(subjectKey: string): string {
  return subjectKey
    .split('→')
    .map((step) => step.trim().toLowerCase().replace(/_/g, ' '))
    .filter((step) => step !== '')
    .map((step) => step.charAt(0).toUpperCase() + step.slice(1))
    .join(' → ');
}

export function runAnalysis(): Promise<AnalysisRun> {
  return apiRequest<AnalysisRun>('/discovery/analysis/run', { method: 'POST' });
}

export function fetchFindings(): Promise<Finding[]> {
  return apiRequest<Finding[]>('/discovery/analysis/findings');
}

export function fetchFindingSubjects(findingId: string): Promise<string[]> {
  return apiRequest<string[]>(
    `/discovery/analysis/findings/${encodeURIComponent(findingId)}/subjects`,
  );
}

export function fetchFindingsTouching(bracketId: string): Promise<Finding[]> {
  return apiRequest<Finding[]>(
    `/discovery/analysis/findings/touching/${encodeURIComponent(bracketId)}`,
  );
}

export function describeStage(stage: Stage): { name: string; asks: string } {
  switch (stage) {
    case 'OBSERVE':
      return { name: 'Observe', asks: 'What is there to look at?' };
    case 'NORMALISE':
      return { name: 'Normalise', asks: 'What is the shape of it?' };
    case 'MEASURE':
      return { name: 'Measure', asks: 'What are the numbers?' };
    case 'DETECT':
      return { name: 'Detect', asks: 'What is a pattern?' };
    case 'CORRELATE':
      return { name: 'Correlate', asks: 'Why?' };
    case 'RECOMMEND':
      return { name: 'Recommend', asks: 'What should we do?' };
  }
}
