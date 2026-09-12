import { apiRequest } from '../../../shared/api/client';

export interface AnalyserAbsence {
  what: string;
  detail: string;
  blocking: boolean;
}

export interface AnalyserClean {
  what: string;
  detail: string;
}

export interface AnalyserPrecondition {
  needed: string;
  had: string;
  met: boolean;

  remedy: string | null;
}

export interface AnalyserLine {
  id: string;
  itemsRead: number;
  findings: number;

  failure: string | null;
  absences: readonly AnalyserAbsence[];
  clean: readonly AnalyserClean[];
  preconditions: readonly AnalyserPrecondition[];
}

export interface AnalyserRun {
  runId: string;
  windowFrom: string;
  windowTo: string;
  findingsTotal: number;

  failure: string | null;
  analysers: readonly AnalyserLine[];
}

export function runAnalysers(windowDays?: number): Promise<AnalyserRun> {
  const query = windowDays === undefined ? '' : `?windowDays=${String(windowDays)}`;
  return apiRequest<AnalyserRun>(`/analysis/runs${query}`, { method: 'POST' });
}

export async function fetchLatestAnalysis(): Promise<AnalyserRun | null> {
  const run = await apiRequest<AnalyserRun | ''>('/analysis/runs/latest');

  return run === '' ? null : run;
}

export interface FindingContext {
  clients: readonly string[];
  projects: readonly string[];
  engagements: number;
  workTypes: readonly string[];
  from: string | null;
  to: string | null;
}

export interface QueuedFinding {
  id: string;
  analyser: string;
  kind: string;

  stage: string;
  subjectKind: string;

  subject: string;

  subjectName: string | null;

  context: FindingContext | null;
  headline: string;
  because: readonly string[];
  severity: string | null;
  confidence: string | null;
  reach: number;

  reachOf: number | null;
  action: string;
  lifecycle: string;

  timesSeen: number;
  firstSeenAt: string;
  priority: number;
  whyItRanks: string;
  evidence: readonly { kind: string; id: string }[];
}

export interface FindingGroup {
  category: string;
  items: readonly QueuedFinding[];
}

export interface QueueStanding {
  shown: number;
  fresh: number;
  worsening: number;
  stillTrue: number;
  improving: number;

  dismissed: number;
  nothingNew: boolean;
}

export interface FindingQueue {
  runId: string;
  windowFrom: string;
  windowTo: string;
  ranAt: string;
  groups: readonly FindingGroup[];
  standing: QueueStanding;
}

export async function fetchFindingQueue(): Promise<FindingQueue | null> {
  const queue = await apiRequest<FindingQueue | ''>('/analysis/findings');
  return queue === '' ? null : queue;
}

export function dismissFinding(findingId: string): Promise<void> {
  return apiRequest<void>(`/analysis/findings/${findingId}/dismiss`, { method: 'POST' });
}
