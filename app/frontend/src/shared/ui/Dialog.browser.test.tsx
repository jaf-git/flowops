import { cleanup, fireEvent, render } from '@testing-library/react';
import { useState, type JSX } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '../../index.css';

import { Button } from './Button';
import { Dialog } from './Dialog';

function Tall({ onCancel }: { onCancel: () => void }): JSX.Element {
  const [rows] = useState(() => Array.from({ length: 60 }, (_, index) => index));

  return (
    <Dialog
      open
      onCancel={onCancel}
      title="Record a process"
      actions={
        <Button variant="secondary" data-dialog-cancel>
          Cancel
        </Button>
      }
    >
      {rows.map((row) => (
        <p key={row}>Step {row + 1}</p>
      ))}
    </Dialog>
  );
}

afterEach(cleanup);

describe('a dialog taller than the viewport', () => {
  it('gives the scrollbar to its body and not to itself', async () => {
    render(<Tall onCancel={vi.fn()} />);

    const dialog = document.querySelector('dialog.ui-dialog') as HTMLDialogElement;
    const body = dialog.querySelector('.ui-dialog-body') as HTMLElement;

    expect(body.scrollHeight).toBeGreaterThan(body.clientHeight);
    expect(dialog.scrollHeight).toBe(dialog.clientHeight);
  });

  it('does not cancel when the click lands inside its own box', async () => {
    const onCancel = vi.fn();
    render(<Tall onCancel={onCancel} />);

    const dialog = document.querySelector('dialog.ui-dialog') as HTMLDialogElement;
    const box = dialog.getBoundingClientRect();

    fireEvent.click(dialog, {
      clientX: Math.round(box.right) - 4,
      clientY: Math.round(box.top + box.height / 2),
    });

    expect(onCancel).not.toHaveBeenCalled();
  });

  it('still cancels when the click lands outside its box', async () => {
    const onCancel = vi.fn();
    render(<Tall onCancel={onCancel} />);

    const dialog = document.querySelector('dialog.ui-dialog') as HTMLDialogElement;
    const box = dialog.getBoundingClientRect();
    expect(box.top).toBeGreaterThan(1);

    fireEvent.click(dialog, {
      clientX: Math.round(box.left + box.width / 2),
      clientY: Math.round(box.top / 2),
    });

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('is centred horizontally rather than pinned to a corner', async () => {
    render(<Tall onCancel={vi.fn()} />);

    const box = (
      document.querySelector('dialog.ui-dialog') as HTMLDialogElement
    ).getBoundingClientRect();
    const leftGap = box.left;
    const rightGap = window.innerWidth - box.right;

    expect(Math.abs(leftGap - rightGap)).toBeLessThanOrEqual(2);
    expect(box.top).toBeGreaterThan(0);
  });
});
