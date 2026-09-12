// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { FindingQueue, QueuedFinding } from '../api/analyserApi';
import { OwnerQueueScreen } from './OwnerQueueScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const dismiss = vi.fn();
let queueState: {
  data?: FindingQueue | null;
  isPending: boolean;
  isError: boolean;
} = { data: null, isPending: false, isError: false };

vi.mock('../hooks/useAnalysis', () => ({
  useFindingQueue: () => queueState,
  useDismissFinding: () => ({ mutate: dismiss, isPending: false }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  queueState = { data: null, isPending: false, isError: false };
});

function finding(over: Partial<QueuedFinding> = {}): QueuedFinding {
  return {
    id: 'f-1',
    analyser: 'S6_LIBRARY',
    kind: 'never_matched_template',
    stage: 'OBSERVE',
    subjectKind: 'WORKSPACE',
    subject: 'workspace',
    subjectName: 'This workspace',
    context: null,
    headline: '41 of 63 approved templates have never matched any work',
    because: ['Caption set, Quarterly retro and 39 more.'],
    severity: 'HIGH',
    confidence: 'HIGH',
    reach: 41,
    reachOf: 63,
    action: 'Review the library',
    lifecycle: 'WORSENING',
    timesSeen: 1,
    firstSeenAt: '2026-06-01T00:00:00Z',
    priority: 0.72,
    whyItRanks: 'High because it touches 41 of 63 and it is worse than last run.',
    evidence: [{ kind: 'TEMPLATE', id: 't-1' }],
    ...over,
  };
}

function queue(over: Partial<FindingQueue> = {}): FindingQueue {
  return {
    runId: 'r-1',
    windowFrom: '2026-06-01T00:00:00Z',
    windowTo: '2026-09-01T00:00:00Z',
    ranAt: '2026-09-01T00:00:00Z',
    groups: [{ category: 'YOUR_LIBRARY', items: [finding()] }],
    standing: {
      shown: 1,
      fresh: 0,
      worsening: 1,
      stillTrue: 0,
      improving: 0,
      dismissed: 0,
      nothingNew: false,
    },
    ...over,
  };
}

describe('the owner works a queue', () => {
  it('says nothing has run rather than showing an empty list', () => {
    render(<OwnerQueueScreen />);

    expect(screen.getByText('queue.never.title')).toBeTruthy();
  });

  it('says so when the queue could not be read', () => {
    queueState = { data: undefined, isPending: false, isError: true };

    render(<OwnerQueueScreen />);

    expect(screen.getByText('queue.failed.title')).toBeTruthy();
  });

  it('shows a finding with the sentence that explains its rank', async () => {
    queueState = { data: queue(), isPending: false, isError: false };

    render(<OwnerQueueScreen />);
    await userEvent.click(screen.getByRole('button', { name: /41 of 63/ }));

    expect(
      screen.getByText('High because it touches 41 of 63 and it is worse than last run.'),
    ).toBeTruthy();
  });

  it('renders the suggested verb as words and offers only the dismissal as a button', async () => {
    queueState = { data: queue(), isPending: false, isError: false };

    render(<OwnerQueueScreen />);
    await userEvent.click(screen.getByRole('button', { name: /41 of 63/ }));

    expect(screen.getByText(/Review the library/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Review the library' })).toBeNull();
    expect(screen.getByRole('button', { name: 'queue.dismiss' })).toBeTruthy();
  });

  it('sends the dismissal for the finding that is open', async () => {
    queueState = { data: queue(), isPending: false, isError: false };

    render(<OwnerQueueScreen />);
    await userEvent.click(screen.getByRole('button', { name: /41 of 63/ }));
    await userEvent.click(screen.getByRole('button', { name: 'queue.dismiss' }));

    expect(dismiss).toHaveBeenCalledOnce();
    expect(dismiss.mock.calls[0]?.[0]).toBe('f-1');
  });

  it('marks exactly one row as current, however many are in the queue', async () => {
    queueState = {
      data: queue({
        groups: [
          {
            category: 'YOUR_LIBRARY',
            items: [
              finding(),
              finding({ id: 'f-2', headline: 'Two kinds of work have no template' }),
            ],
          },
        ],
      }),
      isPending: false,
      isError: false,
    };

    render(<OwnerQueueScreen />);
    await userEvent.click(screen.getByRole('button', { name: /41 of 63/ }));
    await userEvent.click(screen.getByRole('button', { name: /no template/ }));

    const current = screen
      .getAllByRole('button')
      .filter((el) => el.getAttribute('aria-current') === 'true');
    expect(current).toHaveLength(1);
    expect(current[0]?.textContent).toContain('no template');
  });

  it('says nothing is new when nothing is new', () => {
    queueState = {
      data: queue({
        standing: {
          shown: 14,
          fresh: 0,
          worsening: 0,
          stillTrue: 14,
          improving: 0,
          dismissed: 0,
          nothingNew: true,
        },
      }),
      isPending: false,
      isError: false,
    };

    render(<OwnerQueueScreen />);

    expect(screen.getByText(/queue\.nothingNew/)).toBeTruthy();
  });

  it('says how many are being held back because somebody said no', () => {
    queueState = {
      data: queue({
        standing: {
          shown: 1,
          fresh: 0,
          worsening: 1,
          stillTrue: 0,
          improving: 0,
          dismissed: 3,
          nothingNew: false,
        },
      }),
      isPending: false,
      isError: false,
    };

    render(<OwnerQueueScreen />);

    expect(screen.getByText(/queue\.held/)).toBeTruthy();
  });

  it('narrows the list to one lifecycle when a count is pressed, and releases it when pressed again', async () => {
    queueState = {
      data: queue({
        groups: [
          {
            category: 'YOUR_LIBRARY',
            items: [
              finding(),
              finding({
                id: 'f-2',
                headline: 'Two kinds of work have no template',
                lifecycle: 'NEW',
              }),
            ],
          },
        ],
        standing: {
          shown: 2,
          fresh: 1,
          worsening: 1,
          stillTrue: 0,
          improving: 0,
          dismissed: 0,
          nothingNew: false,
        },
      }),
      isPending: false,
      isError: false,
    };

    render(<OwnerQueueScreen />);
    const filters = screen.getByRole('group', { name: 'queue.standing.label' });
    const worse = within(filters).getByRole('button', { name: /queue\.lifecycle\.WORSENING/ });

    await userEvent.click(worse);
    expect(screen.queryByRole('button', { name: /no template/ })).toBeNull();
    expect(worse.getAttribute('aria-pressed')).toBe('true');

    await userEvent.click(worse);
    expect(screen.getByRole('button', { name: /no template/ })).toBeTruthy();
  });

  it('drops the open finding when a filter hides it', async () => {
    queueState = {
      data: queue({
        groups: [
          {
            category: 'YOUR_LIBRARY',
            items: [
              finding(),
              finding({
                id: 'f-2',
                headline: 'Two kinds of work have no template',
                lifecycle: 'NEW',
              }),
            ],
          },
        ],
        standing: {
          shown: 2,
          fresh: 1,
          worsening: 1,
          stillTrue: 0,
          improving: 0,
          dismissed: 0,
          nothingNew: false,
        },
      }),
      isPending: false,
      isError: false,
    };

    render(<OwnerQueueScreen />);
    await userEvent.click(screen.getByRole('button', { name: /41 of 63/ }));
    expect(screen.getByText(/High because it touches 41 of 63/)).toBeTruthy();

    const filters = screen.getByRole('group', { name: 'queue.standing.label' });
    await userEvent.click(within(filters).getByRole('button', { name: /queue\.lifecycle\.NEW/ }));

    expect(screen.queryByText(/High because it touches 41 of 63/)).toBeNull();
    expect(screen.getByText('queue.pick.title')).toBeTruthy();
  });
});
