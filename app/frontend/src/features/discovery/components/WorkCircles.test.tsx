// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { jobKeys } from '../hooks/useJobGraph';
import { WorkCircles } from './WorkCircles';

const apiRequest = vi.fn<(path: string, init?: RequestInit) => Promise<unknown>>();

vi.mock('../../../shared/api/client', () => ({
  apiRequest: (path: string, init?: RequestInit) => apiRequest(path, init),
  ApiError: class extends Error {},
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

const OFFER = {
  describe: 'Summer menu › PHOTO',
  joins: true,
  bracketId: 'b-1',
  workType: 'PHOTO',
  verbs: ['ADD'],
  othersHere: [] as unknown[],
};

const TARIQS = {
  bracketId: 'b-9',
  jobId: 'job-1',
  workType: 'PHOTO',
  destination: 'Summer menu › PHOTO',
  performerId: 'p-9',
  performerName: 'Tariq',
};

const MINE = {
  bracketId: 'b-1',
  jobId: 'job-1',
  workType: 'PHOTO',
  destination: 'Summer menu › PHOTO',
  performerId: 'p-1',
  performerName: 'Sara',
  closureRight: 'p-1',
  holderName: 'Sara',
  waiting: 1,
  waitingHolderNames: ['Karim'],
};

function serve(
  over: {
    jobs?: unknown[];
    marks?: unknown[];
    offer?: Record<string, unknown>;
    deliverable?: Record<string, unknown>;
  } = {},
): void {
  apiRequest.mockImplementation((path, init) => {
    if (path.startsWith('/discovery/jobs')) {
      return Promise.resolve(over.jobs ?? [{ jobId: 'job-1', name: 'Summer menu', guessed: true }]);
    }

    if (path.includes('/marks')) {
      return Promise.resolve(over.marks ?? []);
    }

    if (path.startsWith('/discovery/brackets/preview')) {
      return Promise.resolve({ ...OFFER, ...over.offer });
    }

    if (path.includes('/deliverable')) {
      return Promise.resolve(over.deliverable ?? { mine: [], heldByOthers: [] });
    }

    if (path.includes('/deliver')) {
      return Promise.resolve({
        bracketId: 'b-1',
        destination: 'Summer menu › PHOTO',
        output: 'm-1',
        released: 1,
        unblocked: ['Karim'],
      });
    }

    if (path === '/discovery/work' && init?.method === 'POST') {
      return Promise.resolve({
        nodeId: 'n-1',
        bracketId: 'b-1',
        joined: true,
        destination: 'Summer menu › PHOTO',
        workType: 'PHOTO',
        joinedWith: null,
      });
    }

    return Promise.resolve([]);
  });
}

let cache: QueryClient;

function draw(): ReactElement {
  return (
    <QueryClientProvider client={cache}>
      <WorkCircles
        conversationId="c-1"
        messageId="m-1"
        viewerId="p-1"
        permissions={['WORK_NODE_MARK']}
        people={[{ id: 'p-1', displayName: 'Sara' }]}
        everybody={[
          { id: 'p-1', displayName: 'Sara' },
          { id: 'p-7', displayName: 'Nour' },
        ]}
        onBringIn={() => Promise.resolve()}
      />
    </QueryClientProvider>
  );
}

function sentTo(path: string): Record<string, unknown> {
  const call = apiRequest.mock.calls.find(
    ([called, init]) => called === path && init?.method === 'POST',
  );

  return JSON.parse((call?.[1]?.body as string | undefined) ?? '{}') as Record<string, unknown>;
}

beforeEach(() => {
  apiRequest.mockReset();
  cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

afterEach(cleanup);

async function openTheStrip(): Promise<void> {
  fireEvent.click(await screen.findByRole('button', { name: /discovery.circles.openLabel/ }));

  try {
    fireEvent.click(
      await screen.findByRole(
        'button',
        { name: /discovery.circles.addToEngagement/ },
        { timeout: 400 },
      ),
    );
  } catch {}
}

async function pressDeliver(): Promise<void> {
  fireEvent.click(await screen.findByRole('button', { name: /discovery.circles.deliverLabel/ }));
}

describe('the grey circle offers only what is legal', () => {
  it('adds to work already open at this address, in one press', async () => {
    serve();
    render(draw());

    await openTheStrip();

    const add = await screen.findByRole('button', { name: /discovery.circles.addTo/ });

    expect(screen.queryByRole('button', { name: /discovery.circles.start/ })).toBeNull();

    fireEvent.click(add);

    await waitFor(() => {
      expect(sentTo('/discovery/work')).toMatchObject({ verb: 'ADD', jobId: 'job-1' });
    });
  });

  it('marks without an activity when nobody names one', async () => {
    serve();
    render(draw());

    await openTheStrip();

    fireEvent.click(await screen.findByRole('button', { name: /discovery.circles.addTo/ }));

    await waitFor(() => {
      expect(sentTo('/discovery/work')).toMatchObject({ verb: 'ADD' });
    });

    expect(sentTo('/discovery/work')).not.toHaveProperty('activityId');
  });

  it('never renders a verb the server did not return', async () => {
    serve({ offer: { joins: false, verbs: ['CREATE'], bracketId: null } });
    render(draw());

    await openTheStrip();

    await screen.findByRole('button', { name: /discovery.circles.start/ });
    expect(screen.queryByRole('button', { name: /discovery.circles.addTo/ })).toBeNull();
  });

  it('offers starting your own beside joining somebody, and a join names their work', async () => {
    serve({ offer: { joins: false, verbs: ['CREATE', 'JOIN'], othersHere: [TARIQS] } });
    render(draw());

    await openTheStrip();

    await screen.findByRole('button', { name: /discovery.circles.startOwn/ });
    fireEvent.click(screen.getByRole('button', { name: /discovery.circles.joinOne/ }));

    await waitFor(() => {
      expect(sentTo('/discovery/work')).toMatchObject({ verb: 'JOIN', joining: 'b-9' });
    });
  });

  it('asks which project where two are live, and shows no destination until it is answered', async () => {
    serve({
      jobs: [
        { jobId: 'job-1', name: 'Summer menu', guessed: true },
        { jobId: 'job-2', name: 'Autumn refresh', guessed: false },
      ],
    });
    render(draw());

    expect(screen.queryByText('discovery.mark.whichJob')).toBeNull();
    expect(screen.queryByRole('combobox')).toBeNull();

    await openTheStrip();

    await screen.findByText('discovery.mark.whichJob');
    await screen.findByRole('combobox');
    expect(screen.queryByRole('button', { name: /discovery.circles.addTo/ })).toBeNull();
  });

  it('offers a second engagement beside a live one, so a room is never locked to one client', async () => {
    serve();
    render(draw());

    expect(screen.queryByText('discovery.strip.newJob')).toBeNull();

    fireEvent.click(await screen.findByRole('button', { name: /discovery.circles.openLabel/ }));

    await screen.findByRole('button', { name: /discovery.circles.addToEngagement/ });
    await screen.findByRole('button', { name: 'discovery.strip.newJob' });
  });

  it('invalidates the engagement header, so the inspector stops reading a stale count', async () => {
    serve();

    await cache.fetchQuery({ queryKey: jobKeys.header('job-1'), queryFn: () => ({ live: 3 }) });
    render(draw());

    await openTheStrip();

    fireEvent.click(await screen.findByRole('button', { name: /discovery.circles.addTo/ }));

    await waitFor(() => {
      expect(cache.getQueryState(jobKeys.header('job-1'))?.isInvalidated).toBe(true);
    });
  });
});

const WORK_HERE = {
  messageId: 'm-1',
  nodeId: 'n-1',
  bracketId: 'b-1',
  workType: 'PHOTO',
  address: 'Summer menu › PHOTO',
  project: 'Summer menu',
  client: null,
  title: null,
  state: 'OPEN',
  closeKind: null,
  jobId: 'job-1',
  jobName: 'Summer menu',
  performerId: 'me',
  performerName: 'Sara',
  boundary: false,
};

describe('the red circle reads the closure right before it offers anything', () => {
  it('names what one press would close and who it would unblock', async () => {
    serve({ marks: [WORK_HERE], deliverable: { mine: [MINE], heldByOthers: [] } });
    render(draw());

    await pressDeliver();

    await screen.findByText('discovery.circles.thisCloses');

    await screen.findByText('discovery.circles.unblocks');

    fireEvent.click(screen.getByRole('button', { name: 'discovery.circles.deliverConfirm' }));

    await waitFor(() => {
      expect(sentTo('/discovery/work/m-1/deliver')).toMatchObject({ bracketId: 'b-1' });
    });
  });

  it('refuses by naming the holder rather than offering a press that would fail', async () => {
    serve({ marks: [WORK_HERE], deliverable: { mine: [], heldByOthers: ['Tariq'] } });
    render(draw());

    await pressDeliver();

    await screen.findByText('discovery.circles.heldByOthers');
    expect(screen.queryByRole('button', { name: 'discovery.circles.deliverConfirm' })).toBeNull();
  });

  it('says plainly when nothing here is yours, and offers nothing', async () => {
    serve({ marks: [WORK_HERE], deliverable: { mine: [], heldByOthers: [] } });
    render(draw());

    await pressDeliver();

    await screen.findByText('discovery.circles.nothingOfYours');
    expect(screen.queryByRole('button', { name: 'discovery.circles.deliverConfirm' })).toBeNull();
  });

  it('is absent on the engagement boundary, which is in neither circle', async () => {
    serve({
      marks: [
        {
          messageId: 'm-1',
          nodeId: 'n-0',
          bracketId: 'b-0',
          workType: 'CLIENT_INTAKE',
          address: 'Summer menu',
          state: 'OPEN',
          closeKind: null,
          jobId: 'job-1',
          jobName: 'Summer menu',
          performerName: null,
          boundary: true,
        },
      ],
    });
    render(draw());

    await screen.findByText(/discovery.mark.opensJob/);

    expect(
      await screen.findByRole('button', { name: /discovery.circles.deliverLabel/ }),
    ).toBeDefined();
  });
});

describe('the mark disc is on every message, the deliver disc only where there is work', () => {
  async function markDisc(): Promise<void> {
    await screen.findByRole('button', { name: /discovery.circles.openLabel/ });
  }

  function noDeliverDisc(): void {
    expect(screen.queryByRole('button', { name: /discovery.circles.deliverLabel/ })).toBeNull();
  }

  async function bothCircles(): Promise<void> {
    await markDisc();
    noDeliverDisc();
  }

  it('carries the deliver disc once the message is work', async () => {
    serve({ marks: [WORK_HERE] });
    render(draw());

    await markDisc();
    await screen.findByRole('button', { name: /discovery.circles.deliverLabel/ });
  });

  it('on an ordinary message with one engagement live', async () => {
    serve();
    render(draw());

    await bothCircles();
  });

  it('in a room where no engagement is open yet, and opening one is behind the circle', async () => {
    serve({ jobs: [] });
    render(draw());

    await bothCircles();
    expect(screen.queryByRole('button', { name: 'discovery.strip.newJob' })).toBeNull();

    await openTheStrip();
    await screen.findByRole('button', { name: 'discovery.strip.newJob' });
  });

  it('in a room where two are live', async () => {
    serve({
      jobs: [
        { jobId: 'job-1', name: 'Summer menu', guessed: true },
        { jobId: 'job-2', name: 'Autumn refresh', guessed: false },
      ],
    });
    render(draw());

    await bothCircles();
  });

  it('on a message the viewer has already marked, dimmed and saying why', async () => {
    serve({
      marks: [
        {
          messageId: 'm-1',
          nodeId: 'n-2',
          bracketId: 'b-2',
          workType: 'PHOTO',
          address: 'Summer menu › PHOTO',
          state: 'OPEN',
          closeKind: null,
          jobId: 'job-1',
          jobName: 'Summer menu',
          performerId: 'p-1',
          performerName: 'Sara',
          boundary: false,
        },
      ],
    });
    render(draw());

    await bothCircles();

    const circle = await screen.findByRole('button', { name: /discovery.circles.openLabel/ });
    expect(circle.getAttribute('data-dimmed')).toBe('true');

    fireEvent.click(circle);
    await screen.findByText('discovery.circles.alreadyYours');
  });

  it('on the engagement boundary, which takes no work but still shows the pair', async () => {
    serve({
      marks: [
        {
          messageId: 'm-1',
          nodeId: 'n-0',
          bracketId: 'b-0',
          workType: 'CLIENT_INTAKE',
          address: 'Summer menu',
          state: 'OPEN',
          closeKind: null,
          jobId: 'job-1',
          jobName: 'Summer menu',
          performerName: null,
          boundary: true,
        },
      ],
    });
    render(draw());

    await bothCircles();

    fireEvent.click(await screen.findByRole('button', { name: /discovery.circles.openLabel/ }));

    await screen.findByText('discovery.circles.boundaryTakesNoWork');
  });
});
