// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DiscoveredProcesses } from './DiscoveredProcesses';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const fetchPipelineDecisions = vi.fn();
const fetchDecisionSources = vi.fn();

vi.mock('../api/pipelineDecisionsApi', () => ({
  fetchPipelineDecisions: (...args: unknown[]) => fetchPipelineDecisions(...args),
  fetchDecisionSources: (...args: unknown[]) => fetchDecisionSources(...args),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  fetchPipelineDecisions.mockResolvedValue([
    {
      id: 'd-1',
      findingKind: 'DRAFT_PROCESS',
      subject: 'K:CLIENT_INTAKE -> K:CONTENT -> K:SCHEDULING',
      outcome: 'NUDGE',
      score: 0.641,
      reason: '5 runs, order not shown below 6 runs (0.691)',
      decided: null,
    },
  ]);
  fetchDecisionSources.mockResolvedValue([
    {
      kind: 'JOB',
      id: 'j-1',
      label: 'Editorial run — How we work',
      detail: 'CLOSED',
      conversationId: null,
      jobId: 'j-1',
    },
  ]);
});

function draw(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <DiscoveredProcesses />
    </QueryClientProvider>,
  );
}

describe('the shapes the last run found', () => {
  it('shows each shape with the reason the pipeline itself recorded', async () => {
    draw();

    expect(await screen.findByText('Client intake → Content → Scheduling')).toBeTruthy();

    expect(screen.getByText('5 runs, order not shown below 6 runs (0.691)')).toBeTruthy();
  });

  it('asks for the engagements only once somebody opens the shape', async () => {
    draw();
    await screen.findByText('Client intake → Content → Scheduling');

    expect(fetchDecisionSources).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { expanded: false }));

    expect(await screen.findByText('Editorial run — How we work')).toBeTruthy();
    expect(fetchDecisionSources).toHaveBeenCalledWith('d-1');
  });

  it('draws nothing at all when the run discovered nothing', async () => {
    fetchPipelineDecisions.mockResolvedValue([]);
    draw();

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.queryByText('board.whatRepeats')).toBeNull();
  });
});
