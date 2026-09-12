import { useQueries, useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { fetchJobsForConversation } from '../api/discoveryApi';
import { fetchJobGraph, type JobGraph } from '../api/jobGraphApi';
import { jobKeys } from './useJobGraph';

const MOST_WORTH_OFFERING = 6;

export function useWorkVocabulary(conversationId: string | undefined): string[] {
  const jobs = useQuery({
    queryKey: ['discovery', 'jobs', conversationId ?? '', true],
    queryFn: () => fetchJobsForConversation(conversationId as string, true),
    enabled: conversationId !== undefined,
  });

  const jobIds = (jobs.data ?? []).map((job) => job.jobId);

  const jobKey = jobIds.join(',');

  const graphs = useQueries({
    queries: jobIds.map((jobId) => ({
      queryKey: jobKeys.graph(jobId),
      queryFn: () => fetchJobGraph(jobId),
    })),
  });

  const loaded = graphs.filter((one) => one.data !== undefined).length;

  return useMemo(() => {
    const uses = new Map<string, number>();

    for (const one of graphs) {
      for (const node of (one.data as JobGraph | undefined)?.nodes ?? []) {
        if (node.boundary) {
          continue;
        }

        uses.set(node.workType, (uses.get(node.workType) ?? 0) + 1);
      }
    }

    return [...uses.entries()]
      .sort((a, b) => (b[1] === a[1] ? a[0].localeCompare(b[0]) : b[1] - a[1]))
      .slice(0, MOST_WORTH_OFFERING)
      .map(([workType]) => workType);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- see the note above on `useQueries`
  }, [jobKey, loaded]);
}
