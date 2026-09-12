// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { Canvas, CanvasCard, CanvasLane } from '../api/canvasApi';
import { CanvasScreen } from './CanvasScreen';

const fetchCanvas = vi.fn<(jobId: string) => Promise<Canvas>>();
const endThread = vi.fn();
const fetchJobsForConversation = vi.fn(async () => [
  { jobId: 'job-1', name: 'Aurora — Ramadan', guessed: true },
]);

vi.mock('../api/canvasApi', () => ({
  fetchCanvas: (jobId: string) => fetchCanvas(jobId),
}));

vi.mock('../api/discoveryApi', () => ({
  answerNudge: vi.fn(),
  blockNode: vi.fn(),
  deleteNode: vi.fn(),
  endThread: (trackId: string) => endThread(trackId),
  fetchJobsForConversation: () => fetchJobsForConversation(),
  fetchNudge: vi.fn(),
  markMessage: vi.fn(),
  openJob: vi.fn(),
  recordOutput: vi.fn(),
  relinkNode: vi.fn(),
  resumeNode: vi.fn(),
}));

function lookup(key: string, vars?: Record<string, unknown>): string {
  const found = key
    .split('.')
    .reduce<unknown>(
      (node, step) =>
        typeof node === 'object' && node !== null
          ? (node as Record<string, unknown>)[step]
          : undefined,
      en,
    );

  const text = typeof found === 'string' ? found : ((vars?.defaultValue as string) ?? key);

  return Object.entries(vars ?? {}).reduce(
    (sentence, [name, value]) => sentence.replaceAll(`{{${name}}}`, String(value)),
    text,
  );
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: lookup, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function lane(over: Partial<CanvasLane> = {}): CanvasLane {
  return {
    trackId: 'track-1',
    fromRoleName: 'Account manager',
    toRoleName: 'Content writer',
    state: 'ACTIVE',
    completeness: null,
    closeReason: null,
    weaklyKeyed: false,
    cards: [
      {
        nodeId: 'n1',
        title: 'brief',
        kind: 'WORK',
        direction: 'REQUEST',
        outputType: 'TEXT',
        templated: false,
        phases: [{ phase: 'WORK', ms: 7_200_000 }],

        conversationId: 'conv-1',
        messageId: 'msg-1',
      },
    ],
    loops: [],
    ...over,
  };
}

function aCard(over: Partial<CanvasCard> = {}): CanvasCard {
  const [first] = lane().cards;
  if (first === undefined) {
    throw new Error('the default lane must carry a card');
  }
  return { ...first, ...over };
}

function canvasOf(lanes: CanvasLane[]): Canvas {
  return { jobId: 'job-1', jobName: 'Aurora — Ramadan', lanes };
}

function draw(
  permissions: readonly string[] = ['DISCOVERY_CANVAS_VIEW', 'WORK_NODE_MARK'],

  onOpenMessage?: (conversationId: string, messageId: string) => void,
) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <CanvasScreen
        conversationId="c1"
        permissions={permissions}
        onFindWork={() => undefined}
        onOpenMessage={onOpenMessage}
      />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('the work canvas', () => {
  it('draws one lane per thread, labelled by its role pair', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([
        lane(),
        lane({
          trackId: 'track-2',
          fromRoleName: 'Account manager',
          toRoleName: 'Designer',
          cards: [
            {
              nodeId: 'n2',
              title: 'concept',
              kind: 'WORK',
              direction: 'REQUEST',
              outputType: 'DESIGN',
              templated: false,
              phases: [{ phase: 'WORK', ms: 10_800_000 }],
              conversationId: 'conv-1',
              messageId: 'msg-2',
            },
          ],
        }),
      ]),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-lane').length).toBe(2));
    expect(screen.getByRole('region', { name: 'Account manager ↔ Content writer' })).toBeDefined();
    expect(screen.getByRole('region', { name: 'Account manager ↔ Designer' })).toBeDefined();
  });

  it('collapses a loop to one card carrying its count, not one card per cycle', async () => {
    const members = ['n2', 'n3', 'n4', 'n5', 'n6', 'n7'];

    fetchCanvas.mockResolvedValue(
      canvasOf([
        lane({
          cards: [
            ...lane().cards,
            ...members.map((nodeId, index) => ({
              nodeId,
              title: index % 2 === 0 ? 'review' : 'revision',
              kind: 'WORK' as const,
              direction: 'REQUEST' as const,
              outputType: null,
              templated: false,
              phases: [],
              conversationId: 'conv-1',
              messageId: `msg-${nodeId}`,
            })),
          ],
          loops: [{ memberNodeIds: members, cycleCount: 3, exitCondition: 'APPROVED' }],
        }),
      ]),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-loop').length).toBe(1));

    expect(container.querySelectorAll('.fo-disc-card').length).toBe(1);
    expect(screen.getByText('review ⇄ revision')).toBeDefined();
    expect(container.querySelector('.fo-disc-loop__count')?.textContent).toBe('×3');
  });

  it('renders no person name, given a response that carries none', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([
        {
          ...lane(),

          performerName: 'Andrei Munteanu',
          performerId: 'person-7',
        } as unknown as CanvasLane,
      ]),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-lane').length).toBe(1));

    expect(container.textContent).toContain('Account manager ↔ Content writer');
    expect(container.textContent).not.toContain('Andrei');
    expect(container.textContent).not.toContain('person-7');
  });

  it('names the phase beside every duration and prints no total', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([
        lane({
          cards: [
            {
              nodeId: 'n1',
              title: 'posts',
              kind: 'WORK',
              direction: 'REQUEST',
              outputType: 'TEXT',
              templated: false,
              phases: [
                { phase: 'WORK', ms: 7_200_000 },
                { phase: 'EXTERNAL_WAIT', ms: 3_600_000 },
              ],
              conversationId: 'conv-1',
              messageId: 'msg-1',
            },
          ],
        }),
      ]),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-phase').length).toBe(2));

    const phases = Array.from(container.querySelectorAll('.fo-disc-phase')).map(
      (node) => node.textContent,
    );
    expect(phases).toEqual(['2h work', '1h external wait']);

    expect(container.textContent).not.toContain('3h');
  });

  it('says what it is waiting for rather than looking broken', async () => {
    fetchCanvas.mockResolvedValue(canvasOf([]));

    draw();

    await waitFor(() => expect(screen.getByText(en.discovery.canvas.empty.heading)).toBeDefined());
    expect(screen.getByText(en.discovery.canvas.empty.body)).toBeDefined();
    expect(screen.getByRole('button', { name: en.discovery.canvas.empty.action })).toBeDefined();
  });

  it('says the same thing, and asks the server nothing, when no thread has been opened', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <CanvasScreen permissions={['DISCOVERY_CANVAS_VIEW']} onFindWork={() => undefined} />
      </QueryClientProvider>,
    );

    expect(screen.getByText(en.discovery.canvas.empty.heading)).toBeDefined();
    expect(fetchCanvas).not.toHaveBeenCalled();
  });

  it('ends a thread from its lane header, through the endpoint that already exists', async () => {
    fetchCanvas.mockResolvedValue(canvasOf([lane()]));
    endThread.mockResolvedValue({ completeness: 'COMPLETE', closeReason: 'TERMINAL_OUTPUT' });

    draw();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: en.discovery.canvas.endThread })).toBeDefined(),
    );
    fireEvent.click(screen.getByRole('button', { name: en.discovery.canvas.endThread }));

    await waitFor(() => expect(endThread).toHaveBeenCalledWith('track-1'));
  });

  it('offers no ending to somebody the endpoint would refuse', async () => {
    fetchCanvas.mockResolvedValue(canvasOf([lane()]));

    const { container } = draw(['DISCOVERY_CANVAS_VIEW']);

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-lane').length).toBe(1));
    expect(screen.queryByRole('button', { name: en.discovery.canvas.endThread })).toBeNull();
  });

  it('discloses a thread keyed on its performer alone', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([lane({ fromRoleName: null, toRoleName: null, weaklyKeyed: true })]),
    );

    draw();

    await waitFor(() =>
      expect(screen.getAllByText(en.discovery.canvas.lane.weaklyKeyed).length).toBeGreaterThan(0),
    );
  });

  it('says when a card is the end of a piece of work rather than another piece of it', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([
        lane({
          cards: [
            aCard({ nodeId: 'n1' }),

            aCard({ nodeId: 'n2', direction: 'COMPLETION', messageId: null }),
          ],
        }),
      ]),
    );

    draw();

    await waitFor(() =>
      expect(screen.getByText(en.discovery.canvas.card.role.COMPLETION)).toBeDefined(),
    );

    expect(screen.queryAllByText(en.discovery.canvas.card.role.COMPLETION)).toHaveLength(1);
  });

  it('says which card opened the engagement and which closed it', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([
        lane({
          cards: [
            aCard({ nodeId: 'n1', kind: 'JOB_START', direction: 'STANDALONE' }),
            aCard({ nodeId: 'n2', kind: 'JOB_END', direction: 'COMPLETION', messageId: null }),
          ],
        }),
      ]),
    );

    draw();

    await waitFor(() =>
      expect(screen.getByText(en.discovery.canvas.card.role.JOB_START)).toBeDefined(),
    );
    expect(screen.getByText(en.discovery.canvas.card.role.JOB_END)).toBeDefined();

    expect(screen.queryByText(en.discovery.canvas.card.role.COMPLETION)).toBeNull();
  });

  it('hands the conversation and the message of a card up to whoever composed the screen', async () => {
    const opened = vi.fn();
    fetchCanvas.mockResolvedValue(canvasOf([lane()]));

    draw(['DISCOVERY_CANVAS_VIEW', 'WORK_NODE_MARK'], opened);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: lookup('discovery.canvas.card.openMessageLabel', { title: 'brief' }),
        }),
      ).toBeDefined(),
    );
    fireEvent.click(
      screen.getByRole('button', {
        name: lookup('discovery.canvas.card.openMessageLabel', { title: 'brief' }),
      }),
    );

    expect(opened).toHaveBeenCalledWith('conv-1', 'msg-1');
  });

  it('draws no way through on a card whose conversation and message are absent', async () => {
    fetchCanvas.mockResolvedValue(
      canvasOf([
        lane({
          cards: [
            {
              nodeId: 'n1',
              title: 'brief',
              kind: 'WORK',
              direction: 'REQUEST',
              outputType: 'TEXT',
              templated: false,
              phases: [{ phase: 'WORK', ms: 7_200_000 }],

              conversationId: null,
              messageId: null,
            },
          ],
        }),
      ]),
    );

    const { container } = draw(['DISCOVERY_CANVAS_VIEW', 'WORK_NODE_MARK'], () => undefined);

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-card').length).toBe(1));
    expect(screen.getByText('brief')).toBeDefined();
    expect(screen.queryByText(en.discovery.canvas.card.openMessage)).toBeNull();
    expect(container.querySelectorAll('.fo-disc-card__open').length).toBe(0);
  });

  it('draws no way through when nobody composed a way to a conversation', async () => {
    fetchCanvas.mockResolvedValue(canvasOf([lane()]));

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-card').length).toBe(1));
    expect(container.querySelectorAll('.fo-disc-card__open').length).toBe(0);
  });

  it('says the work could not be drawn rather than showing an empty canvas', async () => {
    fetchCanvas.mockRejectedValue(new Error('nope'));

    draw();

    await waitFor(() => expect(screen.getByText(en.discovery.canvas.cannotRead)).toBeDefined());
    expect(screen.getByText(en.discovery.canvas.cannotReadBody)).toBeDefined();
  });
});
