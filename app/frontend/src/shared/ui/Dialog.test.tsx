// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Button } from './Button';
import { Dialog } from './Dialog';

afterEach(cleanup);

function open(props: Partial<Parameters<typeof Dialog>[0]> = {}): { cancel: () => void } {
  const onCancel = vi.fn();

  render(
    <Dialog
      open
      onCancel={onCancel}
      title="Deactivate Ioana Marinescu?"
      actions={
        <>
          <Button variant="secondary" data-dialog-cancel onClick={onCancel}>
            Cancel
          </Button>
          <Button variant="primary">Deactivate</Button>
        </>
      }
      {...props}
    >
      <p>Ioana will be signed out of every device.</p>
    </Dialog>,
  );

  return { cancel: onCancel };
}

describe('the dialog', () => {
  it('cancels on Escape rather than acting', () => {
    const { cancel } = open();

    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }));

    expect(cancel).toHaveBeenCalledTimes(1);
  });

  function boxedAt(top: number, left: number, bottom: number, right: number): HTMLElement {
    const element = screen.getByRole('dialog');
    vi.spyOn(element, 'getBoundingClientRect').mockReturnValue({
      top,
      left,
      bottom,
      right,
      width: right - left,
      height: bottom - top,
      x: left,
      y: top,
      toJSON: () => ({}),
    } as DOMRect);
    return element;
  }

  it('cancels when the backdrop is clicked', () => {
    const { cancel } = open();

    fireEvent.click(boxedAt(100, 100, 500, 500), { clientX: 20, clientY: 20 });

    expect(cancel).toHaveBeenCalledTimes(1);
  });

  it('does not cancel when the click is inside its own box, target notwithstanding', () => {
    const { cancel } = open();

    fireEvent.click(boxedAt(100, 100, 500, 500), { clientX: 495, clientY: 300 });

    expect(cancel).not.toHaveBeenCalled();
  });

  it('does not cancel when something inside it is clicked', () => {
    const { cancel } = open();

    fireEvent.click(screen.getByText('Ioana will be signed out of every device.'));

    expect(cancel).not.toHaveBeenCalled();
  });

  it('opens with focus on cancel, not on the destructive action', () => {
    open();

    expect(document.activeElement?.textContent).toBe('Cancel');
  });

  it('puts focus in the content where the caller asks for it', () => {
    render(
      <Dialog
        open
        onCancel={vi.fn()}
        title="Erase Andrei Popescu?"
        initialFocus="content"
        actions={
          <Button variant="secondary" data-dialog-cancel>
            Cancel
          </Button>
        }
      >
        <input aria-label="Type the person's name" />
      </Dialog>,
    );

    expect(document.activeElement?.getAttribute('aria-label')).toBe("Type the person's name");
  });

  it('is named by its own heading', () => {
    open();

    expect(screen.getByRole('dialog').getAttribute('aria-labelledby')).toBe(
      screen.getByRole('heading', { name: 'Deactivate Ioana Marinescu?' }).id,
    );
  });
});
