import { apiRequest } from '../../../shared/api/client';

export interface WindowPreview {
  from: string;
  to: string;

  nodesInWindow: number;
  cap: number;

  wouldTruncate: boolean;
}

export interface NodePipelineRun {
  runId: string;
  signature: string;
  windowFrom: string;
  windowTo: string;
  nodesRead: number;
  templatesConsidered: number;
  jobsRead: number;

  stepKindsFound: number;
  processesDiscovered: number;

  discoveryCandidates: number;

  excludedFromDiscovery: number;

  nodesInWindow: number;
}

export function windowOfLastDays(
  days: number,
  now: Date = new Date(),
): { from: string; to: string } {
  const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: now.toISOString() };
}

export async function previewWindow(days: number): Promise<WindowPreview> {
  const { from, to } = windowOfLastDays(days);
  return apiRequest<WindowPreview>(
    `/node-pipeline/runs/preview?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

export interface RunRecord {
  id: string;
  windowFrom: string;
  windowTo: string;
  startedAt: string;
  finishedAt: string | null;
  reachedStage: string;

  signature: string;
  aiMode: string;
  nodesRead: number;

  nodesInWindow: number;
  jobsRead: number;
  failure: string | null;
}

export interface ItemMovement {
  itemId: string;
  itemKind: string;
  beforeStage: string | null;
  beforeReason: string | null;
  afterStage: string | null;
  afterReason: string | null;
}

export interface StageTally {
  stage: string;
  outcome: string;
  count: number;
}

export interface LatestNodeRun {
  run: RunRecord;
  stages: StageTally[];
}

export async function fetchLatestNodeRun(): Promise<LatestNodeRun | null> {
  return apiRequest<LatestNodeRun>('/node-pipeline/runs/latest').catch(() => null);
}

export async function fetchRunHistory(): Promise<RunRecord[]> {
  return apiRequest<RunRecord[]>('/node-pipeline/runs').catch(() => []);
}

export async function fetchRunDiff(pair: {
  before: string;
  after: string;
}): Promise<ItemMovement[]> {
  return apiRequest<ItemMovement[]>(
    `/node-pipeline/runs/${encodeURIComponent(pair.before)}/diff/${encodeURIComponent(pair.after)}`,
  );
}

export interface ComposedProcess {
  templateId: string;
  name: string;
  steps: number;
  observedEdges: number;
  seenInRuns: number;

  blocksApproval: string[];
}

export interface ComposedDiscovery {
  stepKindsFound: number;
  taskTemplatesDrafted: number;
  vocabularyRefinements: number;
  processes: ComposedProcess[];

  alreadyThere: string[];
}

export async function composeDiscoveries(): Promise<ComposedDiscovery> {
  return apiRequest<ComposedDiscovery>('/node-pipeline/compositions', { method: 'POST' });
}

export async function runNodePipeline(days: number): Promise<NodePipelineRun> {
  const { from, to } = windowOfLastDays(days);
  return apiRequest<NodePipelineRun>(
    `/node-pipeline/runs?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    { method: 'POST' },
  );
}

export interface RunComparison {
  runId: string;
  aiMode: string;
  compared: number;
  agreed: number;
  raised: number;
  lowered: number;
  changed: number;

  failed: number;
  modelId: string | null;
  promptVersion: string | null;
}

export async function fetchRunComparison(runId: string): Promise<RunComparison | null> {
  const comparison = await apiRequest<RunComparison | ''>(
    `/node-pipeline/runs/${runId}/comparison`,
  );
  return comparison === '' ? null : comparison;
}

export interface Discovered {
  id: string;
  kind: 'TEMPLATE' | 'PROCESS';
  title: string;
  status: string;
  workType: string | null;
  responsibleRole: string | null;
  description: string | null;
  steps: string[];
}

export interface Guidance {
  id: string;
  kind: string;
  text: string;
  writtenByModel: boolean;
  modelId: string | null;
}

export interface AiState {
  available: boolean;
  on: boolean;
  modelId: string | null;
  promptVersion: string | null;
}

export interface Adoption {
  marks: number;
  naming: number;
  share: number;
}

export async function fetchAdoption(): Promise<Adoption> {
  return apiRequest<Adoption>('/node-pipeline/adoption');
}

export async function fetchDiscoveries(): Promise<Discovered[]> {
  return apiRequest<Discovered[]>('/node-pipeline/discoveries');
}

export async function askForGuidance(id: string): Promise<Guidance> {
  return apiRequest<Guidance>(`/node-pipeline/discoveries/${encodeURIComponent(id)}/guidance`, {
    method: 'POST',
  });
}

export async function fetchAiState(): Promise<AiState> {
  return apiRequest<AiState>('/node-pipeline/ai');
}

export async function setAi(on: boolean): Promise<AiState> {
  return apiRequest<AiState>('/node-pipeline/ai', {
    method: 'PUT',
    body: JSON.stringify({ on }),
  });
}
