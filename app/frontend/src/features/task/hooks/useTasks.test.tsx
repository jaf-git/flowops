// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { JSX, ReactNode } from 'react';

import {
  useAcceptTask,
  useAttachLink,
  useBlockTask,
  useCompleteTask,
  useStartTask,
  useTickChecklistItem,
  useUnblockTask,
} from './useTasks';
import type { Task } from '../api/taskApi';

vi.mock('../api/taskApi', () => ({
  startTask: vi.fn(),
  blockTask: vi.fn(),
  unblockTask: vi.fn(),
  completeTask: vi.fn(),
  acceptTask: vi.fn(),
  createTask: vi.fn(),
  fetchTasks: vi.fn(),
  fetchAssignablePeople: vi.fn(),
  fetchTaskMaterial: vi.fn(),
  attachLink: vi.fn(),
  detachLink: vi.fn(),
  addChecklistItem: vi.fn(),
  tickChecklistItem: vi.fn(),
  removeChecklistItem: vi.fn(),
}));

const api = await import('../api/taskApi');

const TASK: Task = {
  id: 'task-1',
  title: 'Draft the supplier review',
  description: null,
  assigneeId: 'andrei',
  assigneeName: 'Andrei Munteanu',
  creatorId: 'maria',
  deadline: '2026-09-01T09:00:00Z',
  priority: 'NORMAL',
  state: 'IN_PROGRESS',
  selfAssigned: false,
  atRisk: false,
  createdAt: '2026-08-10T09:00:00Z',
};

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }): JSX.Element {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function aQueueInTheCache(): void {
  client.setQueryData(['tasks'], { tasks: [{ id: 'task-1', state: 'ACCEPTED' }] });
}

function theQueueIsStale(): boolean {
  return client.getQueryState(['tasks'])?.isInvalidated === true;
}

beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.mocked(api.startTask).mockResolvedValue(TASK);
  vi.mocked(api.blockTask).mockResolvedValue(TASK);
  vi.mocked(api.unblockTask).mockResolvedValue(TASK);
  vi.mocked(api.completeTask).mockResolvedValue(TASK);
  vi.mocked(api.acceptTask).mockResolvedValue(TASK);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('the transition hooks', () => {
  it('starts work through the endpoint that starts work', async () => {
    const { result } = renderHook(() => useStartTask(), { wrapper });

    result.current.mutate('task-1');

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(vi.mocked(api.startTask).mock.calls[0]?.[0]).toBe('task-1');
  });

  it('blocks with the reason it was given', async () => {
    const { result } = renderHook(() => useBlockTask(), { wrapper });

    result.current.mutate({ id: 'task-1', reason: 'The supplier has not replied' });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(vi.mocked(api.blockTask).mock.calls[0]?.[0]).toEqual({
      id: 'task-1',
      reason: 'The supplier has not replied',
    });
  });

  it.each([
    ['start', () => useStartTask(), 'task-1' as unknown],
    ['block', () => useBlockTask(), { id: 'task-1', reason: 'Waiting on the supplier' } as unknown],
    ['unblock', () => useUnblockTask(), { id: 'task-1', resolution: '' } as unknown],
    [
      'complete',
      () => useCompleteTask(),
      { id: 'task-1', note: 'Done.', externalLink: '' } as unknown,
    ],
    ['accept', () => useAcceptTask(), 'task-1' as unknown],
  ])(
    'marks the queue stale after %s, so the row cannot keep showing the old phase',
    async (_name, hook, input) => {
      aQueueInTheCache();
      expect(theQueueIsStale()).toBe(false);

      const { result } = renderHook(
        hook as () => { mutate: (value: unknown) => void; isSuccess: boolean },
        {
          wrapper,
        },
      );
      result.current.mutate(input);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(theQueueIsStale()).toBe(true);
    },
  );

  it('leaves the queue alone when the transition was refused', async () => {
    aQueueInTheCache();
    vi.mocked(api.blockTask).mockRejectedValue(new Error('refused'));

    const { result } = renderHook(() => useBlockTask(), { wrapper });
    result.current.mutate({ id: 'task-1', reason: 'Waiting on the supplier' });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(theQueueIsStale()).toBe(false);
  });

  it('does not retry a refusal', async () => {
    vi.mocked(api.startTask).mockRejectedValue(new Error('refused'));

    const { result } = renderHook(() => useStartTask(), { wrapper });
    result.current.mutate('task-1');

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(api.startTask).toHaveBeenCalledTimes(1);
  });
});

describe('the material hooks', () => {
  function theMaterialIsStale(id: string): boolean {
    return client.getQueryState(['tasks', 'material', id])?.isInvalidated === true;
  }

  function materialInTheCache(id: string): void {
    client.setQueryData(['tasks', 'material', id], { links: [], checklist: [] });
  }

  it('marks the material stale after a link is attached', async () => {
    materialInTheCache('task-1');
    vi.mocked(api.attachLink).mockResolvedValue({
      id: 'link-1',
      url: 'https://drive.atelier.ro/brief.pdf',
      label: 'Brief for Q3',
      displayText: 'Brief for Q3',
      role: 'INPUT',
      addedById: 'ionut',
      addedAt: '2026-08-13T09:00:00Z',
    });

    const { result } = renderHook(() => useAttachLink(), { wrapper });
    result.current.mutate({
      id: 'task-1',
      url: 'https://drive.atelier.ro/brief.pdf',
      label: 'Brief for Q3',
      role: 'INPUT',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(theMaterialIsStale('task-1')).toBe(true);
  });

  it('leaves the work queue alone, and another task’s material alone', async () => {
    aQueueInTheCache();
    materialInTheCache('task-1');
    materialInTheCache('task-2');
    vi.mocked(api.tickChecklistItem).mockResolvedValue({
      id: 'step-1',
      position: 0,
      text: 'Reconcile the ledger',
      done: true,
      doneAt: '2026-08-13T15:00:00Z',
      authoredById: 'andrei',
    });

    const { result } = renderHook(() => useTickChecklistItem(), { wrapper });
    result.current.mutate({ id: 'task-1', itemId: 'step-1', done: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(theMaterialIsStale('task-1')).toBe(true);
    expect(theMaterialIsStale('task-2')).toBe(false);
    expect(theQueueIsStale()).toBe(false);
  });
});
