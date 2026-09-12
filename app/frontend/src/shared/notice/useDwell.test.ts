// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useDwell } from './useDwell';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

function tick(ms: number): void {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

describe('a notice counts itself down', () => {
  it('expires once its dwell is spent, and not before', () => {
    const expired = vi.fn();
    renderHook(() => useDwell(6_000, expired));

    tick(5_999);
    expect(expired).not.toHaveBeenCalled();

    tick(1);
    expect(expired).toHaveBeenCalledOnce();
  });

  it('never expires when there is no dwell to spend', () => {
    const expired = vi.fn();
    renderHook(() => useDwell(undefined, expired));

    tick(600_000);

    expect(expired).not.toHaveBeenCalled();
  });
});

describe('reaching for a notice does not lose it', () => {
  it('holds for as long as it is held', () => {
    const expired = vi.fn();
    const { result } = renderHook(() => useDwell(6_000, expired));

    act(() => result.current.hold());
    tick(600_000);

    expect(expired).not.toHaveBeenCalled();
    expect(result.current.held).toBe(true);
  });

  it('resumes with only what was left, rather than starting the dwell again', () => {
    const expired = vi.fn();
    const { result } = renderHook(() => useDwell(6_000, expired));

    tick(5_000);
    act(() => result.current.hold());
    tick(600_000);
    act(() => result.current.release());

    tick(999);
    expect(expired).not.toHaveBeenCalled();

    tick(1);
    expect(expired).toHaveBeenCalledOnce();
  });

  it('holding twice does not add time back', () => {
    const expired = vi.fn();
    const { result } = renderHook(() => useDwell(6_000, expired));

    tick(3_000);
    act(() => result.current.hold());
    act(() => result.current.release());
    tick(2_000);
    act(() => result.current.hold());
    act(() => result.current.release());

    tick(999);
    expect(expired).not.toHaveBeenCalled();

    tick(1);
    expect(expired).toHaveBeenCalledOnce();
  });
});
