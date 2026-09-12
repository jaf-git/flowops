import { useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { ConnectionStatus, EventSourceLike } from '../../../shared/realtime/RealtimeClient';
import { useRealtimeStream } from '../../../shared/realtime/useRealtimeStream';
import { fetchCanvasCursor, fetchInstanceForCanvas } from '../api/operationsApi';
import { nodesFromInstance } from '../model/fromInstance';
import type { OperationsCanvasData } from './useOperationsCanvas';

export interface LiveOperationsCanvas {
  instance: UseQueryResult<OperationsCanvasData>;

  status: ConnectionStatus;
}

interface LiveOptions {
  open?: (url: string) => EventSourceLike;
  settleMs?: number;

  silenceMs?: number;
}

export function useLiveOperationsCanvas(
  id: string,
  options: LiveOptions = {},
): LiveOperationsCanvas {
  const client = useQueryClient();
  const { t, i18n } = useTranslation();

  const [generation, setGeneration] = useState(0);

  const cursor = useQuery({
    queryKey: ['canvas', 'stream-cursor', id, generation],
    queryFn: fetchCanvasCursor,

    staleTime: Infinity,
  });

  const instance = useQuery({
    queryKey: ['canvas', 'process-instance', id, cursor.data, i18n.language],
    queryFn: async (): Promise<OperationsCanvasData> => {
      const payload = await fetchInstanceForCanvas(id);
      const { steps, edges } = nodesFromInstance(payload, t);

      return {
        name: payload.name,
        progress: payload.progress,
        steps,
        edges,
        raw: payload.steps,
      };
    },
    enabled: cursor.data !== undefined,

    placeholderData: (previous) => previous,
  });

  const reread = useCallback(() => {
    void client.invalidateQueries({ queryKey: ['canvas', 'process-instance', id] });
  }, [client, id]);

  const restart = useCallback(() => {
    setGeneration((previous) => previous + 1);
  }, []);

  const url =
    cursor.data !== undefined && instance.data !== undefined
      ? `/api/canvas/stream/process-instances/${id}?cursor=${cursor.data}`
      : undefined;

  const status = useRealtimeStream({
    url,
    onDelta: reread,
    onStale: restart,
    open: options.open,
    settleMs: options.settleMs,
    silenceMs: options.silenceMs,
  });

  return { instance, status };
}
