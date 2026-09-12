// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { JSX, ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { EventSourceLike } from '../../../shared/realtime/RealtimeClient';
import type { InstancePayload } from '../model/fromInstance';
import { useLiveOperationsCanvas } from './useLiveOperationsCanvas';

const fetchInstanceForCanvas = vi.fn<(id: string) => Promise<InstancePayload>>();
const fetchCanvasCursor = vi.fn<() => Promise<number>>();

vi.mock('../api/operationsApi', () => ({
  fetchInstanceForCanvas: (id: string) => fetchInstanceForCanvas(id),
  fetchCanvasCursor: () => fetchCanvasCursor(),
}));

class FakeStream implements EventSourceLike {
  static opened: FakeStream[] = [];

  readonly listeners = new Map<string, (event: MessageEvent) => void>();
  readyState = 1;
  closed = false;

  constructor(readonly url: string) {
    FakeStream.opened.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, listener);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  emit(type: string, event: Partial<MessageEvent> = {}): void {
    this.listeners.get(type)?.(event as MessageEvent);
  }

  delta(cursor: number, task: string, kind: string): void {
    this.emit('delta', {
      lastEventId: String(cursor),
      data: JSON.stringify({ task, kind }),
    } as Partial<MessageEvent>);
  }
}

function streamAt(index: number): FakeStream {
  const stream = FakeStream.opened[index];

  if (stream === undefined) {
    throw new Error(`expected a stream at ${index}, and ${FakeStream.opened.length} were opened`);
  }

  return stream;
}

afterEach(() => {
  FakeStream.opened = [];
  fetchInstanceForCanvas.mockReset();
  fetchCanvasCursor.mockReset();
});

function payload(over: Partial<InstancePayload> = {}): InstancePayload {
  return {
    id: 'i1',
    name: 'Integrare — Elena Dobre',
    state: 'RUNNING',
    progress: { closed: 1, total: 3 },
    awaitingAssignment: [],
    edges: [],
    steps: [
      {
        id: 's1',
        title: 'Pregătirea contractului',
        position: 0,
        condition: 'ASSIGNED',
        taskId: 't1',
        taskState: 'IN_PROGRESS',
        blockedReason: null,
        assigneeId: 'p1',
        assigneeName: 'Ana Neagu',
        deadline: null,
        atRisk: false,
        phases: [{ kind: 'ACTIVE', seconds: 3600 }],
      },
    ],
    ...over,
  };
}

function harness() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  const wrapper = ({ children }: { children: ReactNode }): JSX.Element => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );

  return renderHook(
    () => useLiveOperationsCanvas('i1', { open: (url: string) => new FakeStream(url) }),
    { wrapper },
  );
}

describe('the operations canvas on the live channel', () => {
  it('reads the cursor before it reads the snapshot, never beside it', async () => {
    let cursorAnswered: (cursor: number) => void = () => {};
    fetchCanvasCursor.mockReturnValue(
      new Promise<number>((resolve) => {
        cursorAnswered = resolve;
      }),
    );
    fetchInstanceForCanvas.mockResolvedValue(payload());

    harness();

    await waitFor(() => expect(fetchCanvasCursor).toHaveBeenCalledTimes(1));
    expect(fetchInstanceForCanvas).not.toHaveBeenCalled();

    cursorAnswered(50);

    await waitFor(() => expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(1));
  });

  it('subscribes from the cursor the server cut, not from the beginning', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockResolvedValue(payload());

    harness();

    await waitFor(() => expect(FakeStream.opened).toHaveLength(1));
    expect(streamAt(0).url).toBe('/api/canvas/stream/process-instances/i1?cursor=50');
  });

  it('opens no stream while the snapshot is still being read', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockReturnValue(new Promise<InstancePayload>(() => {}));

    harness();

    await waitFor(() => expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(1));
    expect(FakeStream.opened).toHaveLength(0);
  });

  it('re-reads the board when a delta arrives', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockResolvedValue(payload());

    const { result } = harness();

    await waitFor(() => expect(FakeStream.opened).toHaveLength(1));
    expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(1);

    streamAt(0).delta(51, 't1', 'TASK_BLOCKED');

    await waitFor(() => expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(2));
    expect(result.current.instance.data?.name).toBe('Integrare — Elena Dobre');
  });

  it('is unchanged by the same delta arriving twice', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockResolvedValue(payload());

    const { result } = harness();

    await waitFor(() => expect(FakeStream.opened).toHaveLength(1));

    streamAt(0).delta(51, 't1', 'TASK_BLOCKED');
    await waitFor(() => expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(2));
    const once = JSON.stringify(result.current.instance.data);

    streamAt(0).delta(51, 't1', 'TASK_BLOCKED');
    await waitFor(() => expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(3));

    expect(JSON.stringify(result.current.instance.data)).toBe(once);
  });

  it('re-reads the board and resubscribes from a new cursor when the gap is too old', async () => {
    fetchCanvasCursor.mockResolvedValueOnce(50).mockResolvedValueOnce(900);
    fetchInstanceForCanvas.mockResolvedValue(payload());

    harness();

    await waitFor(() => expect(FakeStream.opened).toHaveLength(1));

    streamAt(0).emit('stale', { data: JSON.stringify({ code: 'CURSOR_TOO_OLD' }) });

    await waitFor(() => expect(FakeStream.opened).toHaveLength(2));
    expect(streamAt(1).url).toBe('/api/canvas/stream/process-instances/i1?cursor=900');
    expect(fetchCanvasCursor).toHaveBeenCalledTimes(2);
    expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(2);
  });

  it('reports the connection status the client reports', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockResolvedValue(payload());

    const { result } = harness();

    await waitFor(() => expect(FakeStream.opened).toHaveLength(1));
    expect(result.current.status).toBe('connecting');

    streamAt(0).emit('open');

    await waitFor(() => expect(result.current.status).toBe('live'));
  });
});
