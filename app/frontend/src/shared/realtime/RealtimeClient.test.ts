import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { openRealtimeStream, type EventSourceLike, type RealtimeDelta } from './RealtimeClient';

const CONNECTING = 0;
const OPEN = 1;
const CLOSED = 2;

const SETTLE = 1000;

const SILENCE = 45_000;

class FakeSource implements EventSourceLike {
  readyState = CONNECTING;
  closed = false;

  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>();

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
    this.readyState = CLOSED;
  }

  opens(): void {
    this.readyState = OPEN;
    this.dispatch('open', {});
  }

  drops(): void {
    this.readyState = CONNECTING;
    this.dispatch('error', {});
  }

  givesUp(): void {
    this.readyState = CLOSED;
    this.dispatch('error', {});
  }

  sends(name: string, data: unknown, lastEventId = ''): void {
    this.dispatch(name, { data: JSON.stringify(data), lastEventId });
  }

  sendsRaw(name: string, data: string, lastEventId = ''): void {
    this.dispatch(name, { data, lastEventId });
  }

  private dispatch(type: string, event: Partial<MessageEvent>): void {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event as MessageEvent);
    }
  }
}

interface Harness {
  source: FakeSource;

  sources: () => FakeSource[];
  deltas: RealtimeDelta[];
  statuses: string[];
  staleCount: () => number;
  urls: string[];
  close: () => void;
}

function subscribe(url = '/api/canvas/stream/process-instances/an-instance'): Harness {
  const opened: FakeSource[] = [];
  const deltas: RealtimeDelta[] = [];
  const statuses: string[] = [];
  const urls: string[] = [];
  const onStale = vi.fn();

  const stream = openRealtimeStream({
    url,
    onDelta: (delta) => deltas.push(delta),
    onStale,
    onStatus: (status) => statuses.push(status),
    open: (at) => {
      urls.push(at);
      const source = new FakeSource();
      opened.push(source);
      return source;
    },
    settleMs: SETTLE,
    silenceMs: SILENCE,
  });

  const source = opened[0];
  if (source === undefined) {
    throw new Error('the client opened no connection at all');
  }

  return {
    source,
    sources: () => opened,
    deltas,
    statuses,
    staleCount: () => onStale.mock.calls.length,
    urls,
    close: () => stream.close(),
  };
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('subscribing', () => {
  it('opens the url it was given', () => {
    const { urls } = subscribe('/api/canvas/stream/process-instances/abc?cursor=41');

    expect(urls).toEqual(['/api/canvas/stream/process-instances/abc?cursor=41']);
  });

  it('says connecting before the stream has opened, and never live', () => {
    const { statuses } = subscribe();

    expect(statuses).toEqual(['connecting']);
  });

  it('says live once the stream opens', () => {
    const { source, statuses } = subscribe();

    source.opens();

    expect(statuses).toEqual(['connecting', 'live']);
  });
});

describe('a delta', () => {
  it('carries the identifier, the kind, and the cursor the server wrote as the event id', () => {
    const { source, deltas } = subscribe();
    source.opens();

    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '42');

    expect(deltas).toEqual([{ cursor: 42, task: 'a-task-id', kind: 'TASK_BLOCKED' }]);
  });

  it('is passed on every time it arrives, duplicates included', () => {
    const { source, deltas } = subscribe();
    source.opens();

    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '42');
    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '42');

    expect(deltas).toHaveLength(2);
  });

  it('is dropped rather than thrown when the payload is not the shape the protocol promises', () => {
    const { source, deltas } = subscribe();
    source.opens();

    expect(() => source.sendsRaw('delta', 'not json at all', '42')).not.toThrow();
    expect(() => source.sends('delta', { task: 'a-task-id' }, '42')).not.toThrow();

    expect(deltas).toEqual([]);
  });

  it('is dropped when the server wrote no cursor on it, since a cursor is what makes replay possible', () => {
    const { source, deltas } = subscribe();
    source.opens();

    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '');

    expect(deltas).toEqual([]);
  });
});

describe('a gap too old to replay', () => {
  it('tells the consumer to refetch, and closes the stream rather than letting the browser resume it', () => {
    const { source, staleCount } = subscribe();
    source.opens();

    source.sends('stale', { code: 'CURSOR_TOO_OLD' });

    expect(staleCount()).toBe(1);

    expect(source.closed).toBe(true);
  });

  it('stops saying live, because the board is about to be refetched', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.sends('stale', { code: 'CURSOR_TOO_OLD' });

    expect(statuses).toEqual(['connecting', 'live', 'connecting']);
  });

  it('ignores anything the closed source still dispatches', () => {
    const { source, deltas, statuses } = subscribe();
    source.opens();
    source.sends('stale', { code: 'CURSOR_TOO_OLD' });

    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '43');
    source.opens();

    expect(deltas).toEqual([]);
    expect(statuses).toEqual(['connecting', 'live', 'connecting']);
  });
});

describe('a connection that drops', () => {
  it('says nothing at all for a blip shorter than the settle window', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.drops();
    vi.advanceTimersByTime(SETTLE - 1);
    source.opens();
    vi.advanceTimersByTime(SETTLE);

    expect(statuses).toEqual(['connecting', 'live']);
  });

  it('admits to reconnecting once the drop outlives the settle window', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.drops();
    vi.advanceTimersByTime(SETTLE);

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting']);
  });

  it('says live again when the browser gets back in', () => {
    const { source, statuses } = subscribe();
    source.opens();
    source.drops();
    vi.advanceTimersByTime(SETTLE);

    source.opens();

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting', 'live']);
  });

  it('says offline at once when the browser has given up retrying', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.givesUp();

    expect(statuses).toEqual(['connecting', 'live', 'offline']);
  });

  it('says offline when reconnecting has gone on long enough to stop being a blip', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.drops();
    vi.advanceTimersByTime(SETTLE * 6);

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting', 'offline']);
  });

  it('repeats neither status while it waits', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.drops();
    vi.advanceTimersByTime(SETTLE);
    source.drops();
    source.drops();
    vi.advanceTimersByTime(SETTLE * 10);

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting', 'offline']);
  });
});

describe('closing', () => {
  it('closes the underlying source', () => {
    const { source, close } = subscribe();
    source.opens();

    close();

    expect(source.closed).toBe(true);
  });

  it('reports nothing afterwards, however the source behaves', () => {
    const { source, statuses, deltas, close } = subscribe();
    source.opens();
    close();

    source.drops();
    vi.advanceTimersByTime(SETTLE * 10);
    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '44');

    expect(statuses).toEqual(['connecting', 'live']);
    expect(deltas).toEqual([]);
  });
});

describe('a connection that has gone quiet', () => {
  it('stops claiming live once the beats stop', () => {
    const { source, statuses } = subscribe();
    source.opens();

    source.sendsRaw('beat', '');
    vi.advanceTimersByTime(SILENCE + 1);

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting']);
  });

  it('goes on believing a board where only the beats arrive', () => {
    const { source, statuses } = subscribe();
    source.opens();

    for (let beat = 0; beat < 960; beat += 1) {
      vi.advanceTimersByTime(SILENCE - 1);
      source.sendsRaw('beat', '');
    }

    expect(statuses).toEqual(['connecting', 'live']);
  });

  it('counts a delta as evidence the connection carries bytes', () => {
    const { source, statuses } = subscribe();
    source.opens();

    vi.advanceTimersByTime(SILENCE - 1);
    source.sends('delta', { task: 'a-task-id', kind: 'TASK_BLOCKED' }, '51');
    vi.advanceTimersByTime(SILENCE - 1);

    expect(statuses).toEqual(['connecting', 'live']);
  });

  it('believes the stream again when the beats resume', () => {
    const { source, statuses, sources } = subscribe();
    source.opens();

    vi.advanceTimersByTime(SILENCE + 1);
    sources()[1]?.sendsRaw('beat', '');

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting', 'live']);
  });

  it('opens a new connection when the old one stops speaking', () => {
    const { source, statuses, sources } = subscribe();
    source.opens();

    vi.advanceTimersByTime(SILENCE + 1);

    expect(sources()).toHaveLength(2);
    expect(source.closed).toBe(true);
    expect(statuses).toEqual(['connecting', 'live', 'reconnecting']);
  });

  it('goes live again once the replacement connects', () => {
    const { source, statuses, sources } = subscribe();
    source.opens();

    vi.advanceTimersByTime(SILENCE + 1);
    sources()[1]?.opens();

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting', 'live']);
  });

  it('ignores the connection it threw away', () => {
    const { source, statuses, sources } = subscribe();
    source.opens();

    vi.advanceTimersByTime(SILENCE + 1);
    source.opens();

    expect(statuses).toEqual(['connecting', 'live', 'reconnecting']);
    expect(sources()).toHaveLength(2);
  });

  it('says nothing more once it has been closed', () => {
    const { source, statuses, close } = subscribe();
    source.opens();
    close();

    vi.advanceTimersByTime(SILENCE * 3);

    expect(statuses).toEqual(['connecting', 'live']);
  });
});
