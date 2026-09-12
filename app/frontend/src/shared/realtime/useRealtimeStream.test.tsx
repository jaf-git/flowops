// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { EventSourceLike, RealtimeDelta } from './RealtimeClient';
import { useRealtimeStream } from './useRealtimeStream';

class FakeSource implements EventSourceLike {
  readyState = 0;
  closed = false;

  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>();

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  opens(): void {
    this.readyState = 1;
    this.dispatch('open', {});
  }

  sendsDelta(task: string, cursor: string): void {
    this.dispatch('delta', {
      data: JSON.stringify({ task, kind: 'TASK_BLOCKED' }),
      lastEventId: cursor,
    });
  }

  private dispatch(type: string, event: Partial<MessageEvent>): void {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event as MessageEvent);
    }
  }
}

let opened: { url: string; source: FakeSource }[];

function open(url: string): EventSourceLike {
  const source = new FakeSource();
  opened.push({ url, source });
  return source;
}

function streamAt(index: number): { url: string; source: FakeSource } {
  const stream = opened[index];
  if (stream === undefined) {
    throw new Error(`no stream was opened at ${index}; ${opened.length} were opened in all`);
  }
  return stream;
}

beforeEach(() => {
  opened = [];
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('subscribing', () => {
  it('opens nothing while the snapshot has not landed', () => {
    const { result } = renderHook(() =>
      useRealtimeStream({ url: undefined, onDelta: vi.fn(), onStale: vi.fn(), open }),
    );

    expect(opened).toEqual([]);
    expect(result.current).toBe('connecting');
  });

  it('opens the stream once the snapshot has given it a cursor', () => {
    const { result, rerender } = renderHook(
      ({ url }: { url: string | undefined }) =>
        useRealtimeStream({ url, onDelta: vi.fn(), onStale: vi.fn(), open }),
      { initialProps: { url: undefined as string | undefined } },
    );

    rerender({ url: '/api/canvas/stream/process-instances/abc?cursor=41' });

    expect(opened.map((each) => each.url)).toEqual([
      '/api/canvas/stream/process-instances/abc?cursor=41',
    ]);
    expect(result.current).toBe('connecting');
  });

  it('reports what the client reports', () => {
    const { result } = renderHook(() =>
      useRealtimeStream({ url: '/a-stream', onDelta: vi.fn(), onStale: vi.fn(), open }),
    );

    act(() => streamAt(0).source.opens());

    expect(result.current).toBe('live');
  });

  it('forwards how long silence is tolerated, which is a deployment number', () => {
    vi.useFakeTimers();
    try {
      renderHook(() =>
        useRealtimeStream({
          url: '/a-stream',
          onDelta: vi.fn(),
          onStale: vi.fn(),
          open,
          silenceMs: 90_000,
        }),
      );

      act(() => streamAt(0).source.opens());

      act(() => void vi.advanceTimersByTime(50_000));

      expect(streamAt(0).source.closed).toBe(false);
      expect(opened).toHaveLength(1);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('rendering again', () => {
  it('does not reopen the stream when the callbacks are fresh arrows', () => {
    const { rerender } = renderHook(() =>
      useRealtimeStream({
        url: '/a-stream',
        onDelta: () => undefined,
        onStale: () => undefined,
        open,
      }),
    );

    rerender();
    rerender();
    rerender();

    expect(opened).toHaveLength(1);
    expect(streamAt(0).source.closed).toBe(false);
  });

  it('hands a delta to the callback the latest render passed, not the one it subscribed with', () => {
    const first: RealtimeDelta[] = [];
    const second: RealtimeDelta[] = [];

    const { rerender } = renderHook(
      ({ sink }: { sink: RealtimeDelta[] }) =>
        useRealtimeStream({
          url: '/a-stream',
          onDelta: (delta) => sink.push(delta),
          onStale: vi.fn(),
          open,
        }),
      { initialProps: { sink: first } },
    );

    rerender({ sink: second });
    act(() => streamAt(0).source.sendsDelta('a-task-id', '42'));

    expect(first).toEqual([]);
    expect(second).toEqual([{ cursor: 42, task: 'a-task-id', kind: 'TASK_BLOCKED' }]);
  });
});

describe('the subscription ending', () => {
  it('closes the old stream and opens a new one when the url changes', () => {
    const { rerender } = renderHook(
      ({ url }: { url: string }) =>
        useRealtimeStream({ url, onDelta: vi.fn(), onStale: vi.fn(), open }),
      { initialProps: { url: '/first-instance' } },
    );

    rerender({ url: '/second-instance' });

    expect(opened.map((each) => each.url)).toEqual(['/first-instance', '/second-instance']);
    expect(streamAt(0).source.closed).toBe(true);
    expect(streamAt(1).source.closed).toBe(false);
  });

  it('closes the stream when the screen goes away', () => {
    const { unmount } = renderHook(() =>
      useRealtimeStream({ url: '/a-stream', onDelta: vi.fn(), onStale: vi.fn(), open }),
    );

    unmount();

    expect(streamAt(0).source.closed).toBe(true);
  });
});
