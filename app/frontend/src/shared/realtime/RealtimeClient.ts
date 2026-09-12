export type ConnectionStatus = 'connecting' | 'live' | 'reconnecting' | 'offline';

export interface RealtimeDelta {
  cursor: number;
  task: string;
  kind: string;
}

export interface EventSourceLike {
  addEventListener(type: string, listener: (event: MessageEvent) => void): void;
  close(): void;
  readonly readyState: number;
}

export interface RealtimeStreamOptions {
  url: string;
  onDelta: (delta: RealtimeDelta) => void;

  onStale: () => void;
  onStatus: (status: ConnectionStatus) => void;

  open?: (url: string) => EventSourceLike;

  settleMs?: number;

  silenceMs?: number;
}

export interface RealtimeStream {
  close(): void;
}

const CLOSED = 2;

const DEFAULT_SETTLE_MS = 1000;

const STALL_MULTIPLE = 5;

const DEFAULT_SILENCE_MS = 45_000;

export function openRealtimeStream(options: RealtimeStreamOptions): RealtimeStream {
  const settleMs = options.settleMs ?? DEFAULT_SETTLE_MS;
  const silenceMs = options.silenceMs ?? DEFAULT_SILENCE_MS;
  const open = options.open ?? ((url: string) => new EventSource(url, { withCredentials: true }));

  let stopped = false;
  let status: ConnectionStatus | undefined;
  let settling: ReturnType<typeof setTimeout> | undefined;
  let stalling: ReturnType<typeof setTimeout> | undefined;
  let silent: ReturnType<typeof setTimeout> | undefined;

  function report(next: ConnectionStatus): void {
    if (stopped || next === status) {
      return;
    }
    status = next;
    options.onStatus(next);
  }

  function stopWaiting(): void {
    clearTimeout(settling);
    clearTimeout(stalling);
    settling = undefined;
    stalling = undefined;
  }

  function heard(): void {
    if (stopped) {
      return;
    }
    clearTimeout(silent);
    silent = setTimeout(onSilence, silenceMs);
  }

  function onSilence(): void {
    if (stopped) {
      return;
    }
    report('reconnecting');
    reconnect();
  }

  let source: EventSourceLike;

  function connect(): void {
    const mine = open(options.url);
    source = mine;

    const current = (): boolean => !stopped && source === mine;

    mine.addEventListener('open', () => {
      if (!current()) {
        return;
      }
      stopWaiting();
      heard();
      report('live');
    });

    mine.addEventListener('delta', (event) => {
      if (!current()) {
        return;
      }
      heard();
      const delta = deltaFrom(event);
      if (delta !== undefined) {
        options.onDelta(delta);
      }
    });

    mine.addEventListener('beat', () => {
      if (!current()) {
        return;
      }
      heard();
      report('live');
    });

    mine.addEventListener('stale', () => {
      if (!current()) {
        return;
      }
      stopWaiting();
      clearTimeout(silent);
      mine.close();

      report('connecting');
      stopped = true;
      options.onStale();
    });

    mine.addEventListener('error', () => {
      if (!current()) {
        return;
      }
      if (mine.readyState === CLOSED) {
        stopWaiting();
        report('offline');
        return;
      }
      if (settling !== undefined || stalling !== undefined) {
        return;
      }
      settling = setTimeout(() => {
        settling = undefined;
        report('reconnecting');
      }, settleMs);
      stalling = setTimeout(() => {
        stalling = undefined;
        report('offline');
      }, settleMs * STALL_MULTIPLE);
    });
  }

  function reconnect(): void {
    stopWaiting();
    clearTimeout(silent);
    source.close();
    connect();
  }

  report('connecting');
  connect();

  return {
    close: () => {
      stopped = true;
      stopWaiting();
      clearTimeout(silent);
      source.close();
    },
  };
}

function deltaFrom(event: MessageEvent): RealtimeDelta | undefined {
  const cursor = Number(event.lastEventId);
  if (event.lastEventId === '' || !Number.isFinite(cursor)) {
    return undefined;
  }

  let body: unknown;
  try {
    body = JSON.parse(event.data as string);
  } catch {
    return undefined;
  }

  if (typeof body !== 'object' || body === null) {
    return undefined;
  }

  const { task, kind } = body as Record<string, unknown>;
  return typeof task === 'string' && typeof kind === 'string' ? { cursor, task, kind } : undefined;
}
