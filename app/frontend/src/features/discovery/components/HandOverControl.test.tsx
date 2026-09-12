// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { ConversationBracket, HandOverResponse } from '../api/bracketApi';
import { HandOverControl, type HandoverCandidate } from './HandOverControl';

const handOverBracket =
  vi.fn<(bracketId: string, request: unknown) => Promise<HandOverResponse | undefined>>();

vi.mock('../api/bracketApi', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  handOverBracket: (bracketId: string, request: unknown) => handOverBracket(bracketId, request),
}));

const KARIM: HandoverCandidate = { id: 'p2', displayName: 'Karim Hadad' };
const SARA: HandoverCandidate = { id: 'p3', displayName: 'Sara Popa' };
const ANDREI: HandoverCandidate = { id: 'p1', displayName: 'Andrei Munteanu' };

function bracket(over: Partial<ConversationBracket> = {}): ConversationBracket {
  return {
    bracketId: 'b-1',
    address: 'Summer menu › video',
    workType: 'VIDEO',
    state: 'OPEN',
    closeKind: null,
    outputValue: null,
    outputKind: null,

    performerId: 'p1',
    performerName: 'Andrei Munteanu',
    messageIds: ['m-first', 'm-latest'],
    openedAt: '2026-08-01T09:00:00Z',
    lastActivityAt: '2026-08-02T09:00:00Z',
    nudged: false,
    openWaits: 0,
    live: true,
    ...over,
  };
}

function handed(over: Partial<HandOverResponse> = {}): HandOverResponse {
  return {
    closedBracketId: 'b-1',
    successorBracketId: 'b-2',
    workType: 'VIDEO',
    disrupted: false,
    reTargeted: 0,
    released: 0,
    ...over,
  };
}

function renderControl(over: Partial<Parameters<typeof HandOverControl>[0]> = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <HandOverControl
        conversationId="c-1"
        bracket={bracket()}
        candidates={[ANDREI, KARIM, SARA]}
        {...over}
      />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  handOverBracket.mockReset();
  handOverBracket.mockResolvedValue(handed());
});

afterEach(cleanup);

describe('who the work can go to', () => {
  it('never offers the person already holding it as their own successor', () => {
    renderControl();

    expect(screen.queryByRole('button', { name: 'Andrei Munteanu' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Karim Hadad' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sara Popa' })).toBeTruthy();
  });

  it('says so plainly when there is nobody else, rather than offering an empty row', () => {
    renderControl({ candidates: [ANDREI] });

    expect(screen.getByText(/nobody else active to hand this to/i)).toBeTruthy();
  });

  it('cannot be submitted until somebody is chosen', () => {
    renderControl();

    const act = screen.getByRole('button', { name: /hand this work over/i });
    expect(act.hasAttribute('disabled')).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    expect(act.hasAttribute('disabled')).toBe(false);
  });
});

describe('what it sends', () => {
  it('starts the successor from the bracket’s most recent message, not its first', async () => {
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    await waitFor(() => {
      expect(handOverBracket).toHaveBeenCalledWith('b-1', {
        newPerformerId: 'p2',
        messageId: 'm-latest',
        causedByDeactivation: false,
      });
    });
  });

  it('carries the departure flag only when somebody says they have gone', async () => {
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /no longer here/i }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    await waitFor(() => {
      expect(handOverBracket).toHaveBeenCalledWith(
        'b-1',
        expect.objectContaining({ causedByDeactivation: true }),
      );
    });
  });

  it('defaults to the work simply moving, which is the ordinary case', async () => {
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Sara Popa' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    await waitFor(() => {
      expect(handOverBracket).toHaveBeenCalledWith(
        'b-1',
        expect.objectContaining({ newPerformerId: 'p3', causedByDeactivation: false }),
      );
    });
  });
});

describe('what it says afterwards', () => {
  it('names the successor with the type they inherited', async () => {
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    expect(await screen.findByText(/Karim Hadad now holds this work, still as VIDEO/)).toBeTruthy();
  });

  it('says how many waits moved, and that nobody was told the work arrived', async () => {
    handOverBracket.mockResolvedValue(handed({ reTargeted: 2 }));
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    expect(await screen.findByText(/2 colleagues were waiting on it/)).toBeTruthy();
    expect(screen.getByText(/Nobody was told it had arrived/)).toBeTruthy();
  });

  it('says plainly when nobody was waiting, rather than printing a zero', async () => {
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    expect(await screen.findByText(/Nobody was waiting on it/)).toBeTruthy();
  });

  it('says the successor is set aside from the evidence when the previous person left', async () => {
    handOverBracket.mockResolvedValue(handed({ disrupted: true }));
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /no longer here/i }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    expect(await screen.findByText(/set aside from what the workspace learns/i)).toBeTruthy();
  });

  it('reports an orphan rather than a failure when the server answers with no content', async () => {
    handOverBracket.mockResolvedValue(undefined);
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    expect(await screen.findByText(/no longer held by anybody/i)).toBeTruthy();
    expect(screen.queryByText(/could not be|refused/i)).toBeNull();
  });

  it('says what to do when the server refuses', async () => {
    handOverBracket.mockRejectedValue(new Error('nope'));
    renderControl();

    fireEvent.click(screen.getByRole('button', { name: 'Karim Hadad' }));
    fireEvent.click(screen.getByRole('button', { name: /hand this work over/i }));

    expect(await screen.findByText(/handover was refused/i)).toBeTruthy();
  });
});
