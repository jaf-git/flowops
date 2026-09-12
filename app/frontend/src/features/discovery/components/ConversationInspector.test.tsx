// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { JobOption } from '../api/discoveryApi';
import type { GraphNode, JobGraph, JobHeader } from '../api/jobGraphApi';
import type { ConversationBracket, ConversationWait, MessageMark } from '../api/bracketApi';
import { ConversationInspector } from './ConversationInspector';
import { useReadingMessage } from '../hooks/useReadingMessage';
import { ReadingMessageProvider } from './ReadingMessageProvider';

const fetchJobsForConversation =
  vi.fn<(conversationId: string, describing?: boolean) => Promise<JobOption[]>>();
const fetchJobGraph = vi.fn<(jobId: string) => Promise<JobGraph>>();
const fetchJobHeader = vi.fn<(jobId: string) => Promise<JobHeader>>();
const fetchConversationWork = vi.fn<(id: string) => Promise<ConversationBracket[]>>();
const fetchConversationWaits = vi.fn<(id: string) => Promise<ConversationWait[]>>();

vi.mock('../api/discoveryApi', () => ({
  fetchJobsForConversation: (id: string, describing?: boolean) =>
    fetchJobsForConversation(id, describing),
}));

vi.mock('../api/jobGraphApi', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  fetchJobGraph: (jobId: string) => fetchJobGraph(jobId),
  fetchJobHeader: (jobId: string) => fetchJobHeader(jobId),
  fetchJobArtifacts: () => Promise.resolve([]),
}));

const fetchConversationMarks = vi.fn<(id: string) => Promise<MessageMark[]>>();

vi.mock('../api/bracketApi', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  fetchConversationWork: (id: string) => fetchConversationWork(id),
  fetchConversationWaits: (id: string) => fetchConversationWaits(id),
  fetchConversationMarks: (id: string) => fetchConversationMarks(id),
}));

const AURORA: JobOption = { jobId: 'job-1', name: 'Aurora — Ramadan', guessed: true };

function node(id: string, over: Partial<GraphNode> = {}): GraphNode {
  return {
    nodeId: id,
    bracketId: `b-${id}`,
    workType: 'CONTENT',
    activity: null,
    performerName: 'Sara',
    state: 'OPEN',
    elapsed: 3600,
    phase: 'working',
    closeKind: null,
    nodeRole: 'WORK',
    boundary: false,
    unclaimed: false,

    workTypeOverridden: false,
    department: null,
    markerName: null,
    client: null,
    projectLabel: null,
    text: null,
    title: null,
    detail: null,
    checklist: null,
    direction: null,
    kind: null,
    outputType: null,
    taskTemplateId: null,
    messageId: `m-${id}`,
    conversationId: 'c-1',
    ...over,
  };
}

function header(over: Partial<JobHeader> = {}): JobHeader {
  return {
    jobId: 'job-1',
    name: 'Aurora — Ramadan',
    client: 'Aurora Coffee',
    project: 'Ramadan launch',
    status: 'OPEN',
    closerName: 'Maria Ionescu',
    liveBrackets: 3,
    totalBrackets: 3,
    closeReason: null,
    shapeEligible: true,
    ...over,
  };
}

function bracket(over: Partial<ConversationBracket> = {}): ConversationBracket {
  return {
    bracketId: 'b-1',
    address: 'Aurora › design',
    workType: 'DESIGN',
    state: 'OPEN',
    closeKind: null,
    outputValue: null,
    outputKind: null,
    performerId: 'me',
    performerName: 'Maria Ionescu',
    messageIds: ['m-1'],
    openedAt: '2026-08-01T09:00:00Z',
    lastActivityAt: '2026-08-02T09:00:00Z',
    nudged: false,
    openWaits: 0,
    live: true,
    ...over,
  };
}

function renderInspector(over: Partial<Parameters<typeof ConversationInspector>[0]> = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <ConversationInspector conversationId="c-1" viewerId="me" {...over} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchJobsForConversation.mockReset().mockResolvedValue([AURORA]);
  fetchJobHeader.mockReset().mockResolvedValue(header());
  fetchJobGraph.mockReset().mockResolvedValue({ nodes: [node('a')], edges: [] });
  fetchConversationWork.mockReset().mockResolvedValue([]);
  fetchConversationWaits.mockReset().mockResolvedValue([]);
  fetchConversationMarks.mockReset().mockResolvedValue([]);
});

afterEach(cleanup);

describe('how far the engagement reaches', () => {
  it('counts the distinct conversations its work was marked in', async () => {
    fetchJobGraph.mockResolvedValue({
      nodes: [
        node('a', { conversationId: 'c-1' }),
        node('b', { conversationId: 'c-2' }),
        node('c', { conversationId: 'c-2' }),
        node('d', { conversationId: 'c-3' }),
        node('e', { conversationId: 'c-4' }),
      ],
      edges: [],
    });

    renderInspector();

    expect(await screen.findByText('Spans 4 conversations')).toBeTruthy();
  });

  it('counts the room being read, whether or not a node names it', async () => {
    fetchJobGraph.mockResolvedValue({
      nodes: [node('b', { conversationId: 'c-2' }), node('c', { conversationId: 'c-3' })],
      edges: [],
    });

    renderInspector();

    expect(await screen.findByText('Spans 3 conversations')).toBeTruthy();
  });

  it('does not let a node with no conversation inflate the count', async () => {
    fetchJobGraph.mockResolvedValue({
      nodes: [
        node('a', { conversationId: 'c-1' }),
        node('b', { conversationId: null }),
        node('c', { conversationId: 'c-2' }),
      ],
      edges: [],
    });

    renderInspector();

    expect(await screen.findByText('Spans 2 conversations')).toBeTruthy();
  });

  it('says the work is all here rather than announcing a span of one', async () => {
    renderInspector();

    expect(await screen.findByText(/All of this engagement.s work is in this room/)).toBeTruthy();
    expect(screen.queryByText(/^Spans/)).toBeNull();
  });
});

describe('the other rooms', () => {
  beforeEach(() => {
    fetchJobGraph.mockResolvedValue({
      nodes: [
        node('a', { conversationId: 'c-1' }),
        node('b', { conversationId: 'c-2', workType: 'VIDEO' }),
      ],
      edges: [],
    });
  });

  it('names a room when somebody can say what it is called', async () => {
    renderInspector({ nameOfConversation: () => 'Aurora launch' });

    fireEvent.click(await screen.findByText('Spans 2 conversations'));

    expect(await screen.findByRole('button', { name: 'Aurora launch' })).toBeTruthy();
  });

  it('falls back to the work happening there when no name is known', async () => {
    renderInspector({ nameOfConversation: () => undefined });

    fireEvent.click(await screen.findByText('Spans 2 conversations'));

    expect(await screen.findByRole('button', { name: 'VIDEO' })).toBeTruthy();
  });

  it('opens the room it names', async () => {
    const opened = vi.fn();
    renderInspector({ nameOfConversation: () => 'Aurora launch', onOpenConversation: opened });

    fireEvent.click(await screen.findByText('Spans 2 conversations'));
    fireEvent.click(await screen.findByRole('button', { name: 'Aurora launch' }));

    expect(opened).toHaveBeenCalledWith('c-2');
  });
});

describe('what the card refuses to draw', () => {
  it('renders nothing at all when the conversation has no engagement', async () => {
    fetchJobsForConversation.mockResolvedValue([]);

    const { container } = renderInspector();

    await waitFor(() => {
      expect(fetchJobsForConversation).toHaveBeenCalled();
    });

    expect(container.querySelector('.fo-inspector')).toBeNull();
  });

  it('asks what the room is about, so a finished engagement still has a card', async () => {
    renderInspector();

    await waitFor(() => {
      expect(fetchJobsForConversation).toHaveBeenCalledWith('c-1', true);
    });
  });

  it('shows only the viewer’s own live work, never a colleague’s', async () => {
    fetchConversationWork.mockResolvedValue([
      bracket({ bracketId: 'b-1', workType: 'DESIGN', performerId: 'me' }),
      bracket({ bracketId: 'b-2', workType: 'VIDEO', performerId: 'karim' }),
      bracket({ bracketId: 'b-3', workType: 'ADS', performerId: 'me', live: false }),
    ]);

    renderInspector();

    expect(await screen.findByText('DESIGN')).toBeTruthy();
    expect(screen.queryByText('VIDEO')).toBeNull();

    expect(screen.queryByText('ADS')).toBeNull();
  });

  it('says what to do when none of the room’s work is yours', async () => {
    renderInspector();

    expect(await screen.findByText(/Nothing in this room is yours right now/)).toBeTruthy();
  });
});

describe("one message's work", () => {
  function mark(over: Partial<MessageMark> = {}): MessageMark {
    return {
      messageId: 'm-1',
      nodeId: 'n-1',
      bracketId: 'b-1',
      workType: 'DESIGN',
      activity: null,
      address: 'Aurora › design',

      project: 'Aurora',
      client: null,
      title: null,
      state: 'OPEN',
      closeKind: null,
      jobId: 'job-1',
      jobName: 'Aurora — Ramadan',
      performerId: 'karim',
      performerName: 'Karim',
      boundary: false,
      ...over,
    };
  }

  function Reader({ messageId }: { messageId: string }): ReactElement {
    const reading = useReadingMessage();
    return (
      <button
        type="button"
        onClick={() => {
          reading?.read(messageId);
        }}
      >
        read it
      </button>
    );
  }

  function renderReading(over: Partial<Parameters<typeof ConversationInspector>[0]> = {}) {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    return render(
      <QueryClientProvider client={client}>
        <ReadingMessageProvider>
          <Reader messageId="m-1" />
          <ConversationInspector conversationId="c-1" viewerId="me" {...over} />
        </ReadingMessageProvider>
      </QueryClientProvider>,
    );
  }

  it('is absent entirely where nothing in the room is work', async () => {
    renderReading();

    await screen.findByText('Aurora — Ramadan');
    expect(screen.queryByText("This message's work")).toBeNull();
    expect(screen.queryByText(/Press the line beneath a marked message/)).toBeNull();
  });

  it('invites the reader once the room has work and nothing is selected', async () => {
    fetchConversationMarks.mockResolvedValue([mark()]);
    renderReading();

    expect(await screen.findByText(/Press the line beneath a marked message/)).toBeTruthy();
  });

  it('describes the message that was chosen, and not the room', async () => {
    fetchConversationMarks.mockResolvedValue([mark()]);
    fetchConversationWork.mockResolvedValue([bracket({ bracketId: 'b-1' })]);
    renderReading();

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    expect(await screen.findByText('Aurora › design')).toBeTruthy();

    expect(screen.getByText('Karim')).toBeTruthy();
  });

  it('says how the work ended rather than printing the close kind', async () => {
    fetchConversationMarks.mockResolvedValue([mark({ state: 'CLOSED', closeKind: 'HANDED_OVER' })]);
    renderReading();

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    await screen.findByText('Aurora › design');
    expect(screen.queryByText('HANDED_OVER')).toBeNull();
  });

  it('names what is blocking that one bracket, and not the room’s other waits', async () => {
    fetchConversationMarks.mockResolvedValue([mark()]);

    fetchConversationWork.mockResolvedValue([
      bracket({ bracketId: 'b-1', performerId: 'karim', performerName: 'Karim' }),
    ]);
    fetchConversationWaits.mockResolvedValue([
      {
        waitId: 'w-1',
        bracketId: 'b-1',
        kind: 'CLIENT',
        external: true,
        reason: 'Dana has not approved the budget',
        expectedBy: null,
        openedAt: '2026-08-02T09:00:00Z',
        blockingAddress: null,
      },
      {
        waitId: 'w-2',
        bracketId: 'b-9',
        kind: 'COLLEAGUE',
        external: false,
        reason: 'the captions',
        expectedBy: null,
        openedAt: '2026-08-02T09:00:00Z',
        blockingAddress: null,
      },
    ]);
    renderReading();

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    expect(await screen.findByText('Dana has not approved the budget')).toBeTruthy();
    expect(screen.queryByText('the captions')).toBeNull();
  });

  it('names the other rooms this one piece of work is being done in', async () => {
    fetchConversationMarks.mockResolvedValue([mark()]);
    fetchJobGraph.mockResolvedValue({
      nodes: [
        node('a', { bracketId: 'b-1', conversationId: 'c-1' }),
        node('b', { bracketId: 'b-1', conversationId: 'c-2' }),
        node('c', { bracketId: 'b-9', conversationId: 'c-7' }),
      ],
      edges: [],
    });
    renderReading({ nameOfConversation: (id) => (id === 'c-2' ? "Lena's team" : undefined) });

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    expect(await screen.findByText("Also in Lena's team.")).toBeTruthy();
  });

  it('says nothing about reach when the work has never left this room', async () => {
    fetchConversationMarks.mockResolvedValue([mark()]);
    fetchJobGraph.mockResolvedValue({
      nodes: [node('a', { bracketId: 'b-1', conversationId: 'c-1' })],
      edges: [],
    });
    renderReading();

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    await screen.findByText('Aurora › design');
    expect(screen.queryByText(/Also in/)).toBeNull();
  });

  it('shows the activity somebody named beside the kind of work, because they answer different questions', async () => {
    fetchConversationMarks.mockResolvedValue([mark({ activity: 'Write the caption' })]);
    renderReading();

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    expect(await screen.findByText('Write the caption')).toBeTruthy();
    expect(screen.getByText('DESIGN')).toBeTruthy();
  });

  it('shows no activity where nobody named one, rather than an empty tag', async () => {
    fetchConversationMarks.mockResolvedValue([mark()]);
    renderReading();

    await screen.findByText(/Press the line beneath a marked message/);
    fireEvent.click(screen.getByRole('button', { name: 'read it' }));

    await screen.findByText('DESIGN');
    expect(document.querySelector('[data-kind="activity"]')).toBeNull();
  });
});
