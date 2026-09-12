// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useAssignStep, useAssignablePeople } from './useProcesses';

const fetchAssignablePeople = vi.fn();
const assignStep = vi.fn();

vi.mock('../api/processApi', () => ({
  fetchAssignablePeople: (id: string) => fetchAssignablePeople(id) as unknown,
  assignStep: (instanceId: string, stepId: string, input: unknown) =>
    assignStep(instanceId, stepId, input) as unknown,
  fetchInstance: vi.fn(),
  fetchInstances: vi.fn(),
  fetchTemplate: vi.fn(),
  fetchTemplates: vi.fn(),
  authorTemplate: vi.fn(),
  editTemplate: vi.fn(),
  drawDependency: vi.fn(),
  eraseDependency: vi.fn(),
  startInstance: vi.fn(),
}));

let cache: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={cache}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  fetchAssignablePeople.mockReset();
  assignStep.mockReset();
});

afterEach(() => {
  cache.clear();
});

describe('the process hooks', () => {
  it('caches a run’s assignable people under that run', async () => {
    fetchAssignablePeople.mockImplementation((id: string) =>
      Promise.resolve({ people: [{ id: `${id}-person`, displayName: id }] }),
    );

    const first = renderHook(() => useAssignablePeople('run-1'), { wrapper });
    const second = renderHook(() => useAssignablePeople('run-2'), { wrapper });

    await waitFor(() => {
      expect(first.result.current.data).toBeDefined();
      expect(second.result.current.data).toBeDefined();
    });
    expect(first.result.current.data?.people[0]?.id).toBe('run-1-person');
    expect(second.result.current.data?.people[0]?.id).toBe('run-2-person');
    expect(fetchAssignablePeople).toHaveBeenCalledTimes(2);
  });

  it('asks for nobody while no run is named', () => {
    renderHook(() => useAssignablePeople(null), { wrapper });

    expect(fetchAssignablePeople).not.toHaveBeenCalled();
  });

  it('refreshes the task queues when a step is assigned', async () => {
    const tasks = vi.fn().mockResolvedValue({ tasks: [] });
    await cache.fetchQuery({ queryKey: ['tasks'], queryFn: tasks });
    expect(tasks).toHaveBeenCalledTimes(1);

    const unsubscribe = cache
      .getQueryCache()
      .find({ queryKey: ['tasks'] })
      ?.addObserver({ options: { queryKey: ['tasks'] } } as never);
    void unsubscribe;
    assignStep.mockResolvedValue({ id: 'run-1', steps: [] });

    const { result } = renderHook(() => useAssignStep('run-1'), { wrapper });
    result.current.mutate({ stepId: 'step-1', assigneeId: 'elena', deadline: 'tomorrow' });

    await waitFor(() => {
      expect(cache.getQueryCache().find({ queryKey: ['tasks'] })?.state.isInvalidated).toBe(true);
    });
  });

  it('writes the run the server returned into the cache', async () => {
    assignStep.mockResolvedValue({ id: 'run-1', name: 'Integrare — Andrei', steps: [] });

    const { result } = renderHook(() => useAssignStep('run-1'), { wrapper });
    result.current.mutate({ stepId: 'step-1', assigneeId: 'elena', deadline: 'tomorrow' });

    await waitFor(() => {
      expect(cache.getQueryData(['process-instances', 'run-1'])).toEqual({
        id: 'run-1',
        name: 'Integrare — Andrei',
        steps: [],
      });
    });
  });
});
