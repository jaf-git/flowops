import { useEffect, useRef, useState } from 'react';

import {
  openRealtimeStream,
  type ConnectionStatus,
  type EventSourceLike,
  type RealtimeDelta,
} from './RealtimeClient';

export interface RealtimeSubscription {
  url: string | undefined;
  onDelta: (delta: RealtimeDelta) => void;
  onStale: () => void;

  open?: (url: string) => EventSourceLike;
  settleMs?: number;

  silenceMs?: number;
}

export function useRealtimeStream(subscription: RealtimeSubscription): ConnectionStatus {
  const [status, setStatus] = useState<ConnectionStatus>('connecting');
  const latest = useRef(subscription);

  useEffect(() => {
    latest.current = subscription;
  });

  const { url } = subscription;

  useEffect(() => {
    if (url === undefined) {
      return;
    }

    const stream = openRealtimeStream({
      url,
      onDelta: (delta) => latest.current.onDelta(delta),
      onStale: () => latest.current.onStale(),
      onStatus: setStatus,
      open: latest.current.open,
      settleMs: latest.current.settleMs,
      silenceMs: latest.current.silenceMs,
    });

    return () => stream.close();
  }, [url]);

  return status;
}
