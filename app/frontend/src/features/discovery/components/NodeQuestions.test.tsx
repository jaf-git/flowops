// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { NodeProgress, OutputType, TrackEnding, WaitingOn } from '../api/discoveryApi';
import { NodeQuestions } from './NodeQuestions';

const recordOutput = vi.fn<(nodeId: string, outputType: OutputType) => Promise<NodeProgress>>();
const blockNode = vi.fn<(nodeId: string, waitingOn: WaitingOn) => Promise<NodeProgress>>();
const endThread = vi.fn<(trackId: string) => Promise<TrackEnding>>();

vi.mock('../api/discoveryApi', () => ({
  recordOutput: (nodeId: string, outputType: OutputType) => recordOutput(nodeId, outputType),
  blockNode: (nodeId: string, waitingOn: WaitingOn) => blockNode(nodeId, waitingOn),
  resumeNode: vi.fn(),
  fetchNudge: vi.fn(),
  answerNudge: vi.fn(),
  endThread: (trackId: string) => endThread(trackId),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

function progress(over: Partial<NodeProgress> = {}): NodeProgress {
  return {
    nodeId: 'node-1',
    state: 'IN_PROGRESS',
    outputType: null,
    openPhase: 'WORK',
    waitingOn: null,
    pairedWith: null,
    asksForAnOutput: false,
    ...over,
  };
}

function renderQuestions(trackId: string | null = 'track-1') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <NodeQuestions nodeId="node-1" trackId={trackId} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  recordOutput.mockResolvedValue(progress());
  blockNode.mockResolvedValue(progress({ state: 'BLOCKED', openPhase: 'EXTERNAL_WAIT' }));
  endThread.mockResolvedValue({ completeness: 'COMPLETE', closeReason: 'TERMINAL_OUTPUT' });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Done ▸ produced what?', () => {
  it('offers five answers and pre-selects none of them', () => {
    renderQuestions();

    for (const output of ['TEXT', 'DESIGN', 'REPORT', 'SCHEDULING', 'NONE']) {
      const button = screen.getByText(`discovery.output.answer.${output}`);
      expect(button.getAttribute('aria-pressed')).toBe('false');
    }
  });

  it('records No output yet and leaves the work open', async () => {
    renderQuestions();

    fireEvent.click(screen.getByText('discovery.output.answer.NONE'));

    await waitFor(() => expect(recordOutput).toHaveBeenCalledWith('node-1', 'NONE'));
    await waitFor(() => expect(screen.getByText('discovery.output.noneNote')).toBeTruthy());
    expect(screen.getByText('discovery.blocked.ask')).toBeTruthy();
    expect(screen.getByText('discovery.output.answer.TEXT')).toBeTruthy();
  });

  it('styles No output yet as the quieter answer and the other four alike', () => {
    renderQuestions();

    expect(screen.getByText('discovery.output.answer.NONE').className).toContain(
      'fo-disc-answer--none',
    );
    expect(screen.getByText('discovery.output.answer.TEXT').className).not.toContain(
      'fo-disc-answer--none',
    );
  });

  it('sends the answer the person chose and never a default', async () => {
    renderQuestions();

    fireEvent.click(screen.getByText('discovery.output.answer.DESIGN'));

    await waitFor(() => expect(recordOutput).toHaveBeenCalledWith('node-1', 'DESIGN'));
    expect(recordOutput).toHaveBeenCalledOnce();
  });

  it('shows a refusal plainly and leaves all five answers live', async () => {
    recordOutput.mockRejectedValue(
      new ApiError(409, { code: 'ILLEGAL_NODE_TRANSITION', message: 'no arrow' }),
    );

    renderQuestions();
    fireEvent.click(screen.getByText('discovery.output.answer.TEXT'));

    await waitFor(() =>
      expect(screen.getByText('discovery.error.ILLEGAL_NODE_TRANSITION')).toBeTruthy(),
    );
    for (const output of ['TEXT', 'DESIGN', 'REPORT', 'SCHEDULING', 'NONE']) {
      const button = screen.getByText(`discovery.output.answer.${output}`) as HTMLButtonElement;
      expect(button.disabled).toBe(false);
    }
  });
});

describe('Blocked ▸ waiting on whom?', () => {
  it('offers four answers and no field to type a reason into', () => {
    renderQuestions();

    for (const waiting of ['CLIENT', 'SUPPLIER', 'COLLEAGUE', 'APPROVAL']) {
      expect(screen.getByText(`discovery.blocked.answer.${waiting}`)).toBeTruthy();
    }

    const blocked = screen.getByText('discovery.blocked.ask').closest('.fo-disc-questions');
    expect(blocked).not.toBeNull();
    expect(blocked?.querySelector('input')).toBeNull();
    expect(blocked?.querySelector('textarea')).toBeNull();
  });

  it('carries exactly one free-text question, and it is the one about what the work is', () => {
    const { container } = renderQuestions();

    expect(screen.getByText('discovery.describe.ask')).toBeTruthy();

    const fields = container.querySelectorAll('input, textarea');
    expect(fields).toHaveLength(3);
    for (const field of fields) {
      expect(field.closest('.fo-disc-describe')).not.toBeNull();
    }
  });

  it('sends a different value for a client than for a colleague', async () => {
    renderQuestions();

    fireEvent.click(screen.getByText('discovery.blocked.answer.CLIENT'));
    await waitFor(() => expect(blockNode).toHaveBeenCalledWith('node-1', 'CLIENT'));

    fireEvent.click(screen.getByText('discovery.blocked.answer.COLLEAGUE'));
    await waitFor(() => expect(blockNode).toHaveBeenCalledWith('node-1', 'COLLEAGUE'));

    expect(blockNode.mock.calls.map(([, waitingOn]) => waitingOn)).toEqual(['CLIENT', 'COLLEAGUE']);
  });

  it('marks the answer it took, once the server has taken it', async () => {
    renderQuestions();

    fireEvent.click(screen.getByText('discovery.blocked.answer.SUPPLIER'));

    await waitFor(() =>
      expect(
        screen.getByText('discovery.blocked.answer.SUPPLIER').getAttribute('aria-pressed'),
      ).toBe('true'),
    );
    expect(screen.getByText('discovery.blocked.answer.CLIENT').getAttribute('aria-pressed')).toBe(
      'false',
    );
  });
});

describe('End this thread', () => {
  it('closes the thread and says what the click wrote', async () => {
    renderQuestions('track-1');

    fireEvent.click(screen.getByText('discovery.canvas.endThread'));

    await waitFor(() => expect(endThread).toHaveBeenCalledWith('track-1'));
    await waitFor(() => expect(screen.getByText('discovery.canvas.ended')).toBeTruthy());

    expect(screen.queryByText('discovery.canvas.endThread')).toBeNull();
  });

  it('is absent altogether on work that joined no thread', () => {
    renderQuestions(null);

    expect(screen.queryByText('discovery.canvas.endThread')).toBeNull();
    expect(screen.getByText('discovery.output.ask')).toBeTruthy();
  });

  it('reports a second close as the refusal it is', async () => {
    endThread.mockRejectedValue(
      new ApiError(409, { code: 'TRACK_ALREADY_CLOSED', message: 'already closed' }),
    );

    renderQuestions('track-1');
    fireEvent.click(screen.getByText('discovery.canvas.endThread'));

    await waitFor(() =>
      expect(screen.getByText('discovery.error.TRACK_ALREADY_CLOSED')).toBeTruthy(),
    );
    expect(screen.queryByText('discovery.canvas.ended')).toBeNull();
  });
});
