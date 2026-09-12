// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { StuckGroup } from '../api/stuckApi';
import { stuckReasonInWords } from '../api/stuckWording';
import { StuckPanel } from './StuckPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const formalise = vi.fn();
vi.mock('../hooks/useAnalysis', () => ({
  useFormaliseNode: () => ({ mutate: formalise, isPending: false, isError: false, reset: vi.fn() }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function stuck(over: Partial<StuckGroup> = {}): StuckGroup {
  return {
    itemId: 'n-1',
    itemKind: 'NODE',
    lastStage: 'OBSERVE',
    reason: 'gate:no_work_type',
    count: 11,
    ...over,
  };
}

describe('the stuck ledger', () => {
  it('names the stage a group stopped at and why, in words rather than in the stored code', () => {
    render(<StuckPanel groups={[stuck()]} />);

    expect(screen.getByText('No kind of work recorded')).toBeTruthy();
    expect(screen.queryByText('gate:no_work_type')).toBeNull();
  });

  it('shows one row per reason carrying its count, not one row per item', () => {
    render(
      <StuckPanel
        groups={[
          stuck({ count: 11 }),
          stuck({ reason: 'veto:text_floor', lastStage: 'MEASURE', count: 4 }),
        ]}
      />,
    );

    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getByText('11')).toBeTruthy();
    expect(screen.getByText('4')).toBeTruthy();
  });

  it('says plainly that nothing was dropped rather than rendering an empty list', () => {
    render(<StuckPanel groups={[]} />);

    expect(screen.getByText('pipeline.stuck.none')).toBeTruthy();
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
  });

  it('totals the drops across every group', () => {
    render(
      <StuckPanel groups={[stuck({ count: 11 }), stuck({ reason: 'below_floor', count: 4 })]} />,
    );

    expect(screen.getByText(/pipeline\.stuck\.summary.*15/)).toBeTruthy();
  });

  it('falls through to the raw reason rather than hiding one it cannot phrase', () => {
    expect(stuckReasonInWords('gate:something_new')).toBe('gate:something_new');
    expect(stuckReasonInWords('veto:text_floor')).toBe('The words did not agree');
  });

  it('offers to write down work no template covered', () => {
    render(<StuckPanel groups={[stuck({ reason: 'no_eligible_template' })]} />);

    expect(screen.getByText('pipeline.formalise.open')).toBeTruthy();
  });

  it('offers nothing on a node a gate stopped, because that is not a missing template', () => {
    render(<StuckPanel groups={[stuck({ reason: 'gate:job_boundary' })]} />);

    expect(screen.queryByText('pipeline.formalise.open')).toBeNull();
  });

  it('offers nothing on a run-level row, which has no node to write down', () => {
    render(<StuckPanel groups={[stuck({ itemKind: 'RUN', reason: 'below_floor' })]} />);

    expect(screen.queryByText('pipeline.formalise.open')).toBeNull();
  });

  it('opens the dialog on the node the row names', async () => {
    render(<StuckPanel groups={[stuck({ itemId: 'n-42', reason: 'no_eligible_template' })]} />);

    await userEvent.click(screen.getByText('pipeline.formalise.open'));

    expect(screen.getByText('pipeline.formalise.lede')).toBeTruthy();
  });
});
