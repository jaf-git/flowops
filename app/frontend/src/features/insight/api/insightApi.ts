import { apiDownload, apiRequest } from '../../../shared/api/client';

export interface Phases {
  workMs: number;
  blockedMs: number;
  waitingMs: number;
  reviewMs: number;
}

export type SubjectType = 'process_template' | 'task_template';

export type InsightKind =
  | 'MISSING_STEP'
  | 'SLOW_STEP'
  | 'BLOCK_PATTERN'
  | 'FALSE_DEPENDENCY'
  | 'UNUSED_TEMPLATE'
  | 'ESTIMATE_DIVERGENCE';

export type InsightActionKind =
  'INSERT_STEP' | 'REMOVE_DEPENDENCY' | 'RETIRE_TEMPLATE' | 'UPDATE_ESTIMATE';

export interface Insight {
  kind: InsightKind;
  subjectId: string;
  subjectName: string;

  findingKey: string;
  action: InsightActionKind | null;
  stepTitle: string;

  afterStep: string | null;

  beforeStep: string | null;

  occurrences: number;
  runsTotal: number;

  medianPhases: Phases | null;

  reason: string | null;

  dependsOnStep: string | null;

  daysSinceLastUse: number | null;

  windowDays: number | null;

  expectedIntervalDays: number | null;

  estimatedMs: number | null;

  medianWorkMs: number | null;

  fastestMiddleMs: number | null;
  slowestMiddleMs: number | null;

  spreadIsWide: boolean | null;

  excludedRuns: number | null;

  qualifyingPopulation: number | null;
  windowFrom: string | null;
  windowTo: string | null;

  instances: string[];
}

export function insightsFor(subjectType: SubjectType, subjectId: string): Promise<Insight[]> {
  return apiRequest<Insight[]>(
    `/insights?subject_type=${subjectType}&subject_id=${encodeURIComponent(subjectId)}`,
  );
}

interface Decision {
  subjectType: SubjectType;
  subjectId: string;
  kind: InsightKind;
  findingKey: string;
}

function decisionOn(insight: Insight, subjectType: SubjectType): string {
  return JSON.stringify({
    subjectType,
    subjectId: insight.subjectId,
    kind: insight.kind,
    findingKey: insight.findingKey,
  } satisfies Decision);
}

export function applyInsight(insight: Insight, subjectType: SubjectType): Promise<void> {
  return apiRequest<void>('/insights/apply', {
    method: 'POST',
    body: decisionOn(insight, subjectType),
  });
}

export function dismissInsight(insight: Insight, subjectType: SubjectType): Promise<void> {
  return apiRequest<void>('/insights/dismiss', {
    method: 'POST',
    body: decisionOn(insight, subjectType),
  });
}

export function evidenceFor(templateId: string): Promise<Blob> {
  return apiDownload('/ai-export/scoped', {
    method: 'POST',
    body: JSON.stringify({ subjectType: 'process_template', subjectId: templateId }),
  });
}
