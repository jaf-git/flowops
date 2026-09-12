import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';

import {
  dismissRecommendation,
  fetchFindingSubjects,
  fetchFindings,
  fetchLatestRun,
  fetchRecommendations,
  runAnalysis,
  writeItDown,
  type AnalysisRun,
  type Finding,
  type Recommendation,
  type StoredRun,
  type WrittenDown,
} from '../api/analysisApi';
import { formaliseNode, type Formalised, type FormaliseNode } from '../api/formaliseApi';
import {
  composeDiscoveries,
  fetchLatestNodeRun,
  fetchRunComparison,
  fetchRunDiff,
  fetchRunHistory,
  previewWindow,
  runNodePipeline,
  type ComposedDiscovery,
  type ItemMovement,
  type LatestNodeRun,
  type NodePipelineRun,
  type RunComparison,
  type RunRecord,
  type WindowPreview,
} from '../api/nodePipelineApi';
import { fetchStuck, type StuckGroup } from '../api/stuckApi';
import {
  dismissFinding,
  fetchFindingQueue,
  fetchLatestAnalysis,
  runAnalysers,
  type AnalyserRun,
  type FindingQueue,
} from '../api/analyserApi';

export const analysisKeys = {
  findings: ['discovery', 'analysis', 'findings'] as const,
  recommendations: ['discovery', 'analysis', 'recommendations'] as const,
  latestRun: ['discovery', 'analysis', 'run'] as const,
  subjects: (findingId: string) => ['discovery', 'analysis', 'subjects', findingId] as const,
  stuck: ['node-pipeline', 'run', 'stuck'] as const,

  windowPreview: (days: number) => ['node-pipeline', 'run', 'preview', days] as const,
  runHistory: ['node-pipeline', 'run', 'history'] as const,
  latestNodeRun: ['node-pipeline', 'run', 'latest'] as const,
  runDiff: (pair?: { before: string; after: string }) =>
    ['node-pipeline', 'run', 'diff', pair?.before ?? '', pair?.after ?? ''] as const,

  latestAnalysis: ['analysis', 'run', 'latest'] as const,

  findingQueue: ['analysis', 'findings'] as const,

  comparison: (runId: string) => ['node-pipeline', 'run', 'comparison', runId] as const,
};

export function useFormaliseNode(): UseMutationResult<
  Formalised,
  Error,
  { nodeId: string } & FormaliseNode
> {
  return useMutation({ mutationFn: formaliseNode });
}

export function useLatestNodeRun(): UseQueryResult<LatestNodeRun | null> {
  return useQuery({ queryKey: analysisKeys.latestNodeRun, queryFn: fetchLatestNodeRun });
}

export function useRunHistory(): UseQueryResult<RunRecord[]> {
  return useQuery({ queryKey: analysisKeys.runHistory, queryFn: fetchRunHistory });
}

export function useRunDiff(pair?: {
  before: string;
  after: string;
}): UseQueryResult<ItemMovement[]> {
  return useQuery({
    queryKey: analysisKeys.runDiff(pair),
    queryFn: () => fetchRunDiff(pair as { before: string; after: string }),
    enabled: pair !== undefined,
  });
}

export function useWindowPreview(days: number): UseQueryResult<WindowPreview> {
  return useQuery({
    queryKey: analysisKeys.windowPreview(days),
    queryFn: () => previewWindow(days),
  });
}

export function useRunNodePipeline(): UseMutationResult<NodePipelineRun, Error, number> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: runNodePipeline,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: analysisKeys.stuck });

      void cache.invalidateQueries({ queryKey: analysisKeys.latestNodeRun });
      void cache.invalidateQueries({ queryKey: analysisKeys.runHistory });
    },
  });
}

export function useComposeDiscoveries(): UseMutationResult<ComposedDiscovery, Error, void> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: composeDiscoveries,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ['task-templates'] });
      void cache.invalidateQueries({ queryKey: ['process-templates'] });
    },
  });
}

export function useFindings(): UseQueryResult<Finding[]> {
  return useQuery({ queryKey: analysisKeys.findings, queryFn: fetchFindings });
}

export function useLatestRun(): UseQueryResult<StoredRun | null> {
  return useQuery({ queryKey: analysisKeys.latestRun, queryFn: fetchLatestRun });
}

export function useRecommendations(): UseQueryResult<Recommendation[]> {
  return useQuery({ queryKey: analysisKeys.recommendations, queryFn: fetchRecommendations });
}

export function useRunAnalysis(): UseMutationResult<AnalysisRun, Error, void> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: runAnalysis,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: analysisKeys.findings });

      void cache.invalidateQueries({ queryKey: analysisKeys.recommendations });
      void cache.invalidateQueries({ queryKey: analysisKeys.latestRun });
    },
  });
}

export function useWriteItDown(): UseMutationResult<
  WrittenDown,
  Error,
  { recommendationId: string; name: string }
> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: ({ recommendationId, name }) => writeItDown(recommendationId, name),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: analysisKeys.recommendations });
    },
  });
}

export function useDismissProposal(): UseMutationResult<void, Error, string> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: dismissRecommendation,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: analysisKeys.recommendations });
    },
  });
}

export function useFindingSubjects(findingId: string | undefined): UseQueryResult<string[]> {
  return useQuery({
    queryKey: analysisKeys.subjects(findingId ?? ''),
    queryFn: () => fetchFindingSubjects(findingId as string),
    enabled: findingId !== undefined,
  });
}

export function useStuck(): UseQueryResult<StuckGroup[]> {
  return useQuery({ queryKey: analysisKeys.stuck, queryFn: fetchStuck });
}

export function useLatestAnalysis(): UseQueryResult<AnalyserRun | null> {
  return useQuery({ queryKey: analysisKeys.latestAnalysis, queryFn: fetchLatestAnalysis });
}

export function useRunAnalysers(): UseMutationResult<AnalyserRun, unknown, number | undefined> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (windowDays?: number) => runAnalysers(windowDays),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: analysisKeys.latestAnalysis });
    },
  });
}

export function useFindingQueue(): UseQueryResult<FindingQueue | null> {
  return useQuery({ queryKey: analysisKeys.findingQueue, queryFn: fetchFindingQueue });
}

export function useDismissFinding(): UseMutationResult<void, Error, string> {
  const cache = useQueryClient();

  return useMutation({
    mutationFn: dismissFinding,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: analysisKeys.findingQueue });
    },
  });
}

export function useRunComparison(runId: string | undefined): UseQueryResult<RunComparison | null> {
  return useQuery({
    queryKey: analysisKeys.comparison(runId ?? ''),
    queryFn: () => fetchRunComparison(runId ?? ''),
    enabled: runId !== undefined,
  });
}
