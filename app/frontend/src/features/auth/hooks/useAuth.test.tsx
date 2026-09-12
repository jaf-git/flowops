// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { JSX, ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useResetToken } from './useAuth';

function withClient(): ({ children }: { children: ReactNode }) => JSX.Element {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return function Wrapper({ children }: { children: ReactNode }): JSX.Element {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

const realFetch = globalThis.fetch;

beforeEach(() => {
  vi.restoreAllMocks();
});

afterEach(() => {
  globalThis.fetch = realFetch;
});

describe('the reset link query', () => {
  it('treats a bodiless 204 as the link being good, not as a failure', async () => {
    globalThis.fetch = vi.fn(async () => new Response(null, { status: 204 })) as typeof fetch;

    const { result } = renderHook(() => useResetToken('a-live-token'), { wrapper: withClient() });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.isError).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('still fails when the server refuses the link', async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(JSON.stringify({ code: 'RESET_TOKEN_UNUSABLE', message: 'gone' }), {
          status: 410,
          headers: { 'Content-Type': 'application/json' },
        }),
    ) as typeof fetch;

    const { result } = renderHook(() => useResetToken('a-dead-token'), { wrapper: withClient() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('asks nothing at all when there is no token', () => {
    const asked = vi.fn();
    globalThis.fetch = asked as unknown as typeof fetch;

    const { result } = renderHook(() => useResetToken(''), { wrapper: withClient() });

    expect(asked).not.toHaveBeenCalled();
    expect(result.current.isError).toBe(false);
  });
});
