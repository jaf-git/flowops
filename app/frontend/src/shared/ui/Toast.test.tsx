// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Toast, ToastRegion } from './Toast';

afterEach(cleanup);

describe('the toast region', () => {
  it('is a live region even while it is empty', () => {
    render(<ToastRegion label="Recent updates">{null}</ToastRegion>);

    const region = screen.getByRole('status');
    expect(region.getAttribute('aria-live')).toBe('polite');
    expect(region.getAttribute('aria-label')).toBe('Recent updates');
    expect(region.textContent).toBe('');
  });

  it('announces politely rather than interrupting', () => {
    render(
      <ToastRegion label="Recent updates">
        <Toast>The invitation was withdrawn.</Toast>
      </ToastRegion>,
    );

    expect(screen.getByRole('status').getAttribute('aria-live')).toBe('polite');
    expect(screen.getByText('The invitation was withdrawn.')).toBeTruthy();
  });

  it('offers a dismiss control only when dismissing is possible', () => {
    const { rerender } = render(<Toast>Nothing to dismiss.</Toast>);
    expect(screen.queryByRole('button')).toBeNull();

    const dismiss = vi.fn();
    rerender(
      <Toast onDismiss={dismiss} dismissLabel="Dismiss">
        Something to dismiss.
      </Toast>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(dismiss).toHaveBeenCalledOnce();
  });
});
