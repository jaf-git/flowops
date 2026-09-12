// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { InstancePayload } from '../model/fromInstance';
import { ProcessCanvasScreen } from './ProcessCanvasScreen';

const fetchInstanceForCanvas = vi.fn<(id: string) => Promise<InstancePayload>>();
const fetchCanvasCursor = vi.fn<() => Promise<number>>();

vi.mock('../api/operationsApi', () => ({
  fetchInstanceForCanvas: (id: string) => fetchInstanceForCanvas(id),
  fetchCanvasCursor: () => fetchCanvasCursor(),
}));

class FakeEventSource {
  static opened: FakeEventSource[] = [];

  readonly listeners = new Map<string, (event: MessageEvent) => void>();
  readyState = 1;

  constructor(readonly url: string) {
    FakeEventSource.opened.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, listener);
  }

  close(): void {
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

function liveStream(): FakeEventSource {
  const stream = FakeEventSource.opened.at(-1);

  if (stream === undefined) {
    throw new Error('no stream was opened');
  }

  return stream;
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) => {
      const count = values?.count;
      const candidates =
        typeof count === 'number' ? [`${key}_${count === 1 ? 'one' : 'other'}`, key] : [key];

      for (const candidate of candidates) {
        const phrase = candidate
          .split('.')
          .reduce<unknown>(
            (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
            en as unknown,
          );

        if (typeof phrase === 'string') {
          return phrase.replace(/{{(\w+)}}/g, (_, name: string) => String(values?.[name] ?? ''));
        }
      }

      return key;
    },
    i18n: { language: 'en' },
  }),
}));

beforeAll(() => {
  global.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as unknown as typeof ResizeObserver;

  global.DOMMatrixReadOnly = class {
    m22 = 1;
  } as unknown as typeof DOMMatrixReadOnly;

  Object.defineProperties(global.HTMLElement.prototype, {
    offsetHeight: { get: () => 800 },
    offsetWidth: { get: () => 1200 },
  });

  (
    global as unknown as { SVGElement: { prototype: Record<string, unknown> } }
  ).SVGElement.prototype.getBBox = () => ({ x: 0, y: 0, width: 0, height: 0 });

  global.EventSource = FakeEventSource as unknown as typeof EventSource;
});

afterEach(() => {
  cleanup();
  FakeEventSource.opened = [];
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

function show(
  instance: InstancePayload,
  props: Partial<Parameters<typeof ProcessCanvasScreen>[0]> = {},
) {
  fetchInstanceForCanvas.mockResolvedValue(instance);

  fetchCanvasCursor.mockResolvedValue(50);

  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={client}>
      <ProcessCanvasScreen instanceId="i1" {...props} />
    </QueryClientProvider>,
  );
}

describe('the operations canvas on a real instance', () => {
  it('names the run and draws its steps', async () => {
    show(payload());

    expect(await screen.findByText('Integrare — Elena Dobre')).toBeTruthy();
    expect(screen.getByText('Pregătirea contractului')).toBeTruthy();
  });

  it('leads with the stall when a step is waiting for somebody', async () => {
    show(payload({ awaitingAssignment: ['s1'] }));

    const banner = await screen.findByTestId('canvas-stall-banner');

    expect(banner.textContent).toBe(en.canvas.instance.awaiting_one);
  });

  it('reads as calm progress when nothing is waiting, never as an empty alarm', async () => {
    show(payload());

    const banner = await screen.findByTestId('canvas-stall-banner');

    expect(banner.textContent).toBe('1 of 3 steps closed.');
  });

  it('counts steps and never people, on the banner or anywhere near it', async () => {
    show(payload({ awaitingAssignment: ['s1'] }));

    const banner = await screen.findByTestId('canvas-stall-banner');

    expect(banner.textContent).not.toContain('Ana Neagu');
    expect(banner.textContent).toContain('step');
  });

  it('renders every figure inside a region entitled to carry one, on real work too', async () => {
    const { container } = show(
      payload({
        awaitingAssignment: ['s1'],
        steps: [
          {
            id: 's1',
            title: 'Crearea conturilor IT',
            position: 3,
            condition: 'ASSIGNED',
            taskId: 't1',
            taskState: 'BLOCKED',
            blockedReason: 'Se așteaptă reînnoirea licenței',
            assigneeId: 'p1',
            assigneeName: 'Dan Stan',
            deadline: '2026-08-11T09:00:00Z',
            atRisk: false,
            phases: [
              { kind: 'ACTIVE', seconds: 7200 },
              { kind: 'BLOCKED', seconds: 259200 },
            ],
          },
        ],
      }),
    );

    await screen.findByText('Crearea conturilor IT');

    const permitted = [
      'canvas-node-step',
      'canvas-node-title',
      'canvas-node-meta-row',
      'canvas-node-blocker',
      'canvas-node-note',
      'canvas-node-phases',
    ]
      .map((region) => `[data-testid="${region}"]`)
      .join(',');

    const offenders: string[] = [];

    for (const node of container.querySelectorAll('.canvas-node')) {
      for (const element of node.querySelectorAll('*')) {
        const text = element.textContent ?? '';

        if (
          element.children.length === 0 &&
          /\d/.test(text) &&
          element.closest(permitted) === null
        ) {
          offenders.push(text);
        }
      }
    }

    expect(offenders).toEqual([]);
  });

  it('opens the step when its node is chosen, and closes it again', async () => {
    show(payload());

    expect(screen.queryByTestId('step-inspector')).toBeNull();

    fireEvent.click(await screen.findByText('Pregătirea contractului'));

    const inspector = await screen.findByTestId('step-inspector');
    expect(inspector.getAttribute('aria-label')).toBe('Pregătirea contractului');

    fireEvent.click(screen.getByRole('button', { name: en.canvas.inspector.close }));
    expect(screen.queryByTestId('step-inspector')).toBeNull();
  });

  it('offers no move it cannot carry out, rather than a button that fails silently', async () => {
    show(payload());

    fireEvent.click(await screen.findByText('Pregătirea contractului'));
    await screen.findByTestId('step-inspector');

    expect(screen.queryByRole('button', { name: en.task.block.action })).toBeNull();
    expect(screen.queryByRole('button', { name: en.canvas.inspector.openTask })).toBeNull();
  });

  it('says one thing for an instance that is absent and one that is not yours to see', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockRejectedValue(new Error('not found'));

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ProcessCanvasScreen instanceId="i1" />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByText(en.canvas.instance.unavailable)).toBeTruthy());
    expect(screen.queryByText(/permission/i)).toBeNull();
  });
});

describe('where the moves come from', () => {
  it('states the facts a move turns on, and leaves the move to whoever may build it', async () => {
    const build = vi.fn().mockReturnValue(<button type="button">pretend-to-act</button>);

    show(
      payload({
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
            deadline: '2026-09-01T09:00:00Z',
            atRisk: false,
            phases: [{ kind: 'ACTIVE', seconds: 3600 }],
          },
        ],
      }),
      { viewerId: 'p1', renderTaskActions: build },
    );

    fireEvent.click(await screen.findByText('Pregătirea contractului'));
    await screen.findByTestId('step-inspector');

    expect(build).toHaveBeenCalledWith({
      taskId: 't1',
      state: 'IN_PROGRESS',
      mine: true,
      hasDeadline: true,
      deadline: '2026-09-01T09:00:00Z',
    });
    expect(screen.getByRole('button', { name: 'pretend-to-act' })).toBeTruthy();
  });

  it('does not read somebody else’s work as the viewer’s own', async () => {
    const build = vi.fn().mockReturnValue(<button type="button">pretend-to-act</button>);

    show(payload(), { viewerId: 'somebody-else', renderTaskActions: build });

    fireEvent.click(await screen.findByText('Pregătirea contractului'));
    await screen.findByTestId('step-inspector');

    expect(build).toHaveBeenCalledWith(expect.objectContaining({ mine: false }));
  });

  it('asks for no move on a step that is not yet work', async () => {
    const build = vi.fn();

    show(
      payload({
        steps: [
          {
            id: 's1',
            title: 'Pregătirea contractului',
            position: 0,
            condition: 'PENDING',
            taskId: null,
            taskState: null,
            blockedReason: null,
            assigneeId: null,
            assigneeName: null,
            deadline: null,
            atRisk: false,
            phases: [],
          },
        ],
      }),
      { viewerId: 'p1', renderTaskActions: build },
    );

    fireEvent.click(await screen.findByText('Pregătirea contractului'));
    await screen.findByTestId('step-inspector');

    expect(build).not.toHaveBeenCalled();
  });

  it('asks for no move on a state this build has never heard of', async () => {
    const build = vi.fn();

    show(
      payload({
        steps: [
          {
            id: 's1',
            title: 'Pregătirea contractului',
            position: 0,
            condition: 'ASSIGNED',
            taskId: 't1',
            taskState: 'SOMETHING_NEW',
            blockedReason: null,
            assigneeId: 'p1',
            assigneeName: 'Ana Neagu',
            deadline: null,
            atRisk: false,
            phases: [],
          },
        ],
      }),
      { viewerId: 'p1', renderTaskActions: build },
    );

    fireEvent.click(await screen.findByText('Pregătirea contractului'));
    await screen.findByTestId('step-inspector');

    expect(build).not.toHaveBeenCalled();
  });
});

describe('the plane’s one call to action', () => {
  function ready() {
    return payload({
      awaitingAssignment: ['s1'],
      steps: [
        {
          id: 's1',
          title: 'Pregătirea contractului',
          position: 0,
          condition: 'REACHABLE',
          taskId: null,
          taskState: null,
          blockedReason: null,
          assigneeId: null,
          assigneeName: null,
          deadline: null,
          atRisk: false,
          phases: [],
        },
      ],
    });
  }

  it('opens the assignment from the node itself, in one press', async () => {
    const build = vi.fn().mockReturnValue(<p>pretend-to-assign</p>);

    show(ready(), { renderAssign: build });

    await screen.findByText('Pregătirea contractului');
    expect(screen.queryByText('pretend-to-assign')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: en.canvas.node.assign }));

    expect(screen.getByText('pretend-to-assign')).toBeTruthy();
    expect(build).toHaveBeenCalledWith(expect.objectContaining({ instanceId: 'i1', stepId: 's1' }));
  });

  it('draws no control at all where nobody supplied a way to assign', async () => {
    show(ready());

    await screen.findByText('Pregătirea contractului');

    expect(screen.queryByRole('button', { name: en.canvas.node.assign })).toBeNull();
  });

  it('closes the assign dialog without re-reading the board itself', async () => {
    let handed: { onAssigned: () => void } | undefined;
    const build = vi.fn((subject: { onAssigned: () => void }) => {
      handed = subject;
      return <p>pretend-to-assign</p>;
    });

    show(ready(), { renderAssign: build });

    await screen.findByText('Pregătirea contractului');
    fireEvent.click(screen.getByRole('button', { name: en.canvas.node.assign }));

    expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(1);
    handed?.onAssigned();

    await waitFor(() => expect(screen.queryByText('pretend-to-assign')).toBeNull());

    expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(1);
  });
});

describe('what the board does once something has actually moved', () => {
  it('re-reads itself when a delta arrives, with nobody touching it', async () => {
    show(payload());

    await screen.findByText('Pregătirea contractului');
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1));
    expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(1);

    liveStream().delta(51, 't1', 'TASK_BLOCKED');

    await waitFor(() => expect(fetchInstanceForCanvas).toHaveBeenCalledTimes(2));
  });

  it('subscribes to this instance from the cursor read before the snapshot', async () => {
    show(payload());

    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1));
    expect(liveStream().url).toBe('/api/canvas/stream/process-instances/i1?cursor=50');
  });
});

describe('the surface states a live plane has to render', () => {
  it('loads as node-shaped skeletons rather than a spinner', async () => {
    fetchCanvasCursor.mockResolvedValue(50);
    fetchInstanceForCanvas.mockReturnValue(new Promise<InstancePayload>(() => {}));

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ProcessCanvasScreen instanceId="i1" />
      </QueryClientProvider>,
    );

    const skeleton = await screen.findByTestId('operations-plane-skeleton');

    expect(skeleton.getAttribute('aria-busy')).toBe('true');
    expect(screen.getAllByTestId('node-skeleton').length).toBeGreaterThan(1);
    expect(screen.queryByRole('progressbar')).toBeNull();
  });

  it('says the board is live once the stream is open', async () => {
    show(payload());

    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1));
    liveStream().emit('open');

    expect(await screen.findByText(en.ui.connection.live)).toBeTruthy();
  });

  it('desaturates as soon as the stream drops, not only once it gives up', async () => {
    show(payload());

    await screen.findByText('Pregătirea contractului');
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1));

    const stream = liveStream();
    stream.readyState = 0;
    stream.emit('error');

    expect(
      await screen.findByText(en.ui.connection.reconnecting, {}, { timeout: 4000 }),
    ).toBeTruthy();
    expect(screen.getByText('Pregătirea contractului')).toBeTruthy();
    expect(screen.getByTestId('operations-plane').closest('[data-stale="true"]')).toBeTruthy();
  });

  it('desaturates but keeps the plane when the stream is gone', async () => {
    show(payload());

    await screen.findByText('Pregătirea contractului');
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1));

    const stream = liveStream();
    stream.readyState = 2;
    stream.emit('error');

    expect(await screen.findByText(en.ui.connection.offline)).toBeTruthy();

    expect(screen.getByText('Pregătirea contractului')).toBeTruthy();
    expect(screen.getByTestId('operations-plane').closest('[data-stale="true"]')).toBeTruthy();
  });
});
