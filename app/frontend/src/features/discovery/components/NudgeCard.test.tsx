// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { NodeProgress, NudgeAnswer, NudgeSubject, OutputType } from '../api/discoveryApi';
import { NudgeCard } from './NudgeCard';

const fetchNudge = vi.fn<() => Promise<NudgeSubject | null>>();
const answerNudge = vi.fn<(nodeId: string, answer: NudgeAnswer) => Promise<NodeProgress>>();
const recordOutput = vi.fn<(nodeId: string, outputType: OutputType) => Promise<NodeProgress>>();

vi.mock('../api/discoveryApi', () => ({
  fetchNudge: () => fetchNudge(),
  answerNudge: (nodeId: string, answer: NudgeAnswer) => answerNudge(nodeId, answer),
  recordOutput: (nodeId: string, outputType: OutputType) => recordOutput(nodeId, outputType),
  blockNode: vi.fn(),
  resumeNode: vi.fn(),
  endThread: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

const POSTS: NudgeSubject = {
  nodeId: 'node-1',
  jobId: 'job-1',
  trackId: 'track-1',
  text: 'Sara, can you write the posts for Aurora this week?',
  state: 'ASSIGNED',
};

function progress(over: Partial<NodeProgress> = {}): NodeProgress {
  return {
    nodeId: 'node-1',
    state: 'COMPLETED',
    outputType: null,
    openPhase: 'REVIEW',
    waitingOn: null,
    pairedWith: null,
    asksForAnOutput: false,
    ...over,
  };
}

function renderNudge() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <NudgeCard />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  fetchNudge.mockResolvedValue(POSTS);
  answerNudge.mockResolvedValue(progress());
  recordOutput.mockResolvedValue(progress());
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('the nudge', () => {
  it('asks about one unit of work, with four answers', async () => {
    renderNudge();

    await waitFor(() => expect(screen.getByText('discovery.nudge.ask')).toBeTruthy());
    for (const answer of ['DONE', 'STILL_GOING', 'DROPPED', 'WAS_A_QUESTION']) {
      expect(screen.getByText(`discovery.nudge.answer.${answer}`)).toBeTruthy();
    }
  });

  it('renders nothing at all when there is nothing to ask', async () => {
    fetchNudge.mockResolvedValue(null);

    const { container } = renderNudge();

    await waitFor(() => expect(fetchNudge).toHaveBeenCalledOnce());
    expect(container.querySelector('.fo-disc-nudge')).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByText('discovery.nudge.answer.DONE')).toBeNull();
  });

  it('sends the answer the person pressed', async () => {
    renderNudge();

    await waitFor(() =>
      expect(screen.getByText('discovery.nudge.answer.STILL_GOING')).toBeTruthy(),
    );
    fireEvent.click(screen.getByText('discovery.nudge.answer.STILL_GOING'));

    await waitFor(() => expect(answerNudge).toHaveBeenCalledWith('node-1', 'STILL_GOING'));
  });

  it('shows a refused answer plainly and keeps the work offerable', async () => {
    answerNudge.mockRejectedValue(
      new ApiError(409, { code: 'ILLEGAL_NODE_TRANSITION', message: 'no arrow drawn' }),
    );

    renderNudge();
    await waitFor(() => expect(screen.getByText('discovery.nudge.answer.DROPPED')).toBeTruthy());
    fireEvent.click(screen.getByText('discovery.nudge.answer.DROPPED'));

    await waitFor(() =>
      expect(screen.getByText('discovery.error.ILLEGAL_NODE_TRANSITION')).toBeTruthy(),
    );

    expect(screen.getByText('discovery.nudge.ask')).toBeTruthy();
    for (const answer of ['DONE', 'STILL_GOING', 'DROPPED', 'WAS_A_QUESTION']) {
      const button = screen.getByText(`discovery.nudge.answer.${answer}`) as HTMLButtonElement;
      expect(button.disabled).toBe(false);
      expect(button.getAttribute('aria-pressed')).toBe('false');
    }
  });

  it('asks what it produced when Done did not say', async () => {
    answerNudge.mockResolvedValue(progress({ asksForAnOutput: true }));

    renderNudge();
    await waitFor(() => expect(screen.getByText('discovery.nudge.answer.DONE')).toBeTruthy());
    expect(screen.queryByText('discovery.output.ask')).toBeNull();

    fireEvent.click(screen.getByText('discovery.nudge.answer.DONE'));

    await waitFor(() => expect(screen.getByText('discovery.output.ask')).toBeTruthy());
    for (const output of ['TEXT', 'DESIGN', 'REPORT', 'SCHEDULING', 'NONE']) {
      expect(screen.getByText(`discovery.output.answer.${output}`)).toBeTruthy();
    }
  });

  it('sets the question aside without answering it', async () => {
    const { container } = renderNudge();

    await waitFor(() => expect(screen.getByText('discovery.nudge.dismiss')).toBeTruthy());
    fireEvent.click(screen.getByText('discovery.nudge.dismiss'));

    expect(container.querySelector('.fo-disc-nudge')).toBeNull();
    expect(answerNudge).not.toHaveBeenCalled();
  });
});
