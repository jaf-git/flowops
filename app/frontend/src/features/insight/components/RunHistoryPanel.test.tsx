// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ItemMovement, RunRecord } from '../api/nodePipelineApi';
import { RunHistoryPanel } from './RunHistoryPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const history = vi.fn();
const diff = vi.fn();

vi.mock('../hooks/useAnalysis', () => ({
  useRunHistory: () => history(),
  useRunDiff: (pair?: { before: string; after: string }) => diff(pair),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function run(over: Partial<RunRecord> = {}): RunRecord {
  return {
    id: 'run-1',
    windowFrom: '2026-08-01T00:00:00Z',
    windowTo: '2026-08-31T00:00:00Z',
    startedAt: '2026-08-31T09:10:00Z',
    finishedAt: '2026-08-31T09:10:01Z',
    reachedStage: 'RECOMMEND',
    signature: '9e823c30e638bc52',
    aiMode: 'OFF',
    nodesRead: 83,
    nodesInWindow: 83,
    jobsRead: 11,
    failure: null,
    ...over,
  };
}

function settled(runs: RunRecord[]): void {
  history.mockReturnValue({ isPending: false, isError: false, data: runs });
}

function movements(rows: ItemMovement[] | undefined): void {
  diff.mockReturnValue({
    isPending: false,
    isError: false,
    data: rows,
  });
}

describe('RunHistoryPanel', () => {
  it('invites a comparison rather than shrugging when no run exists yet', () => {
    settled([]);
    movements(undefined);

    render(<RunHistoryPanel />);

    expect(screen.getByText('pipeline.runs.none')).not.toBeNull();
  });

  it('does not fetch a diff until two runs are chosen', async () => {
    settled([run({ id: 'a', signature: 'aaaaaa11' }), run({ id: 'b', signature: 'bbbbbb22' })]);
    movements(undefined);

    render(<RunHistoryPanel />);
    expect(diff).toHaveBeenLastCalledWith(undefined);

    await userEvent.click(screen.getByRole('button', { name: /aaaaaa/ }));

    expect(diff).toHaveBeenLastCalledWith(undefined);
  });

  it('diffs in the order the runs were chosen, so the arrow points the right way', async () => {
    settled([
      run({ id: 'newer', signature: 'aaaaaa11' }),
      run({ id: 'older', signature: 'bbbbbb22' }),
    ]);
    movements([]);

    render(<RunHistoryPanel />);

    await userEvent.click(screen.getByRole('button', { name: /bbbbbb/ }));
    await userEvent.click(screen.getByRole('button', { name: /aaaaaa/ }));

    expect(diff).toHaveBeenLastCalledWith({ before: 'older', after: 'newer' });
  });

  it('says the runs agree rather than showing an empty space', async () => {
    settled([run({ id: 'a', signature: 'aaaaaa11' }), run({ id: 'b', signature: 'bbbbbb22' })]);
    movements([]);

    render(<RunHistoryPanel />);
    await userEvent.click(screen.getByRole('button', { name: /aaaaaa/ }));
    await userEvent.click(screen.getByRole('button', { name: /bbbbbb/ }));

    expect(screen.getByText('pipeline.runs.identical')).not.toBeNull();
  });

  it('says a side was not recorded rather than claiming the item passed', async () => {
    settled([run({ id: 'a', signature: 'aaaaaa11' }), run({ id: 'b', signature: 'bbbbbb22' })]);
    movements([
      {
        itemId: 'n-1',
        itemKind: 'NODE',
        beforeStage: 'MEASURE',
        beforeReason: 'below_floor',
        afterStage: null,
        afterReason: null,
      },
    ]);

    render(<RunHistoryPanel />);
    await userEvent.click(screen.getByRole('button', { name: /aaaaaa/ }));
    await userEvent.click(screen.getByRole('button', { name: /bbbbbb/ }));

    expect(screen.getByText('pipeline.runs.notRecorded')).not.toBeNull();
    expect(screen.getByText(/MEASURE/)).not.toBeNull();
  });
});
