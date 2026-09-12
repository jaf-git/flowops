// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NodePipelineRunControl } from './NodePipelineRunControl';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const preview = vi.fn();
const mutate = vi.fn();
const runState: { isPending: boolean; isError: boolean; data: unknown } = {
  isPending: false,
  isError: false,
  data: undefined,
};

vi.mock('../hooks/useAnalysis', () => ({
  useWindowPreview: (days: number) => preview(days),
  useRunNodePipeline: () => ({ ...runState, mutate }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  runState.isPending = false;
  runState.isError = false;
  runState.data = undefined;
});

function holding(nodesInWindow: number, wouldTruncate = false): void {
  preview.mockReturnValue({
    isPending: false,
    isError: false,
    data: { nodesInWindow, cap: 200, wouldTruncate, from: '', to: '' },
  });
}

describe('NodePipelineRunControl', () => {
  it('says how much the window holds before anything is run', () => {
    holding(83);

    render(<NodePipelineRunControl />);

    expect(screen.getByText(/pipeline\.nodeRun\.holds.*83/)).not.toBeNull();
  });

  it('warns that the cap will drop the oldest, rather than truncating silently', () => {
    holding(348, true);

    render(<NodePipelineRunControl />);

    expect(screen.getByText(/pipeline\.nodeRun\.holds.*348/)).not.toBeNull();
    expect(screen.getByText(/pipeline\.nodeRun\.capped/)).not.toBeNull();
  });

  it('offers a wider window when this one holds nothing, and refuses to run', () => {
    holding(0);

    render(<NodePipelineRunControl />);

    expect(screen.getByText('pipeline.nodeRun.empty')).not.toBeNull();
    expect(
      (screen.getByRole('button', { name: 'pipeline.nodeRun.run' }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('counts again when the span changes, so the number always describes the chosen window', async () => {
    holding(83);

    render(<NodePipelineRunControl />);
    expect(preview).toHaveBeenCalledWith(30);

    await userEvent.click(screen.getByRole('button', { name: /pipeline\.nodeRun\.days.*90/ }));

    expect(preview).toHaveBeenCalledWith(90);
  });

  it('runs the window the person chose, not the default', async () => {
    holding(83);

    render(<NodePipelineRunControl />);
    await userEvent.click(screen.getByRole('button', { name: /pipeline\.nodeRun\.days.*180/ }));
    await userEvent.click(screen.getByRole('button', { name: 'pipeline.nodeRun.run' }));

    expect(mutate).toHaveBeenCalledWith(180);
  });

  it('says the run recorded nothing when it fails, rather than going quiet', () => {
    holding(83);
    runState.isError = true;

    render(<NodePipelineRunControl />);

    expect(screen.getByRole('alert').textContent).toBe('pipeline.nodeRun.failed');
  });

  it('reports a count that could not be taken instead of showing a stale one', () => {
    preview.mockReturnValue({ isPending: false, isError: true, data: undefined });

    render(<NodePipelineRunControl />);

    expect(screen.getByText(/^pipeline\.nodeRun\.countFailed/)).not.toBeNull();
  });
});
