import { useQueries, useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { fetchJobsForConversation } from '../api/discoveryApi';
import { fetchJobGraph, type JobGraph } from '../api/jobGraphApi';
import { jobKeys } from './useJobGraph';

export function useBracketReach(conversationId: string | undefined): Map<string, string[]> {
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
    const rooms = new Map<string, Set<string>>();

    for (const one of graphs) {
      for (const node of (one.data as JobGraph | undefined)?.nodes ?? []) {
        if (node.conversationId === null || node.conversationId === conversationId) {
          continue;
        }

        const seen = rooms.get(node.bracketId) ?? new Set<string>();
        seen.add(node.conversationId);
        rooms.set(node.bracketId, seen);
      }
    }

    const reach = new Map<string, string[]>();
    for (const [bracketId, seen] of rooms) {
      reach.set(bracketId, [...seen]);
    }

    return reach;
    // eslint-disable-next-line react-hooks/exhaustive-deps -- see the note above on `useQueries`
  }, [conversationId, jobKey, loaded]);
}
