// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { BlockTaskDialog } from './BlockTaskDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const blockMutate = vi.fn();
const blockReset = vi.fn();
let blockState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useTasks', () => ({
  useBlockTask: () => ({ ...blockState, mutate: blockMutate, reset: blockReset }),
}));

beforeEach(() => {
  blockState = { isPending: false, error: undefined };
  blockMutate.mockReset();
  blockReset.mockReset();
});

afterEach(cleanup);

function open() {
  return render(
    <BlockTaskDialog taskId="task-1" onClose={() => undefined} onBlocked={() => undefined} />,
  );
}

function submitButton(): HTMLButtonElement {
  return screen.getByText('task.block.submit').closest('button') as HTMLButtonElement;
}

describe('BlockTaskDialog', () => {
  it('will not submit an empty reason, because an unexplained block is unactionable', () => {
    open();

    expect(submitButton().disabled).toBe(true);
  });

  it('will not submit a reason of spaces either', () => {
    open();
    fireEvent.change(screen.getByLabelText('task.block.field.reason'), {
      target: { value: '    ' },
    });

    expect(submitButton().disabled).toBe(true);
  });

  it('submits once something has actually been said, which is what makes the guard mean something', () => {
    open();
    fireEvent.change(screen.getByLabelText('task.block.field.reason'), {
      target: { value: 'The supplier has not sent the figures' },
    });

    expect(submitButton().disabled).toBe(false);
    fireEvent.click(submitButton());

    expect(blockMutate).toHaveBeenCalledTimes(1);
    expect(blockMutate.mock.calls[0]?.[0]).toEqual({
      id: 'task-1',
      reason: 'The supplier has not sent the figures',
    });
  });

  it('disables submission while the request is in flight', () => {
    blockState = { isPending: true, error: undefined };
    open();
    fireEvent.change(screen.getByLabelText('task.block.field.reason'), {
      target: { value: 'The supplier has not replied' },
    });

    const submitting = screen
      .getByText('task.block.submitting')
      .closest('button') as HTMLButtonElement;
    expect(submitting.disabled).toBe(true);
    expect(screen.queryByText('task.block.submit')).toBeNull();
  });

  it('says which rule refused, rather than that something went wrong', () => {
    blockState = {
      isPending: false,
      error: new ApiError(409, { code: 'ILLEGAL_TRANSITION', message: 'This task has moved on.' }),
    };
    open();

    expect(screen.getByText('task.block.error.ILLEGAL_TRANSITION')).toBeTruthy();
  });

  it('falls back to the general sentence for a code it has no words for', () => {
    blockState = {
      isPending: false,
      error: new ApiError(500, { code: 'SOMETHING_NEW', message: 'Unexpected.' }),
    };
    open();

    expect(screen.getByText('task.block.error.SOMETHING_NEW')).toBeTruthy();
  });

  it('asks what the work is waiting on, not why the person has not finished', () => {
    open();

    expect(screen.getByRole('dialog').getAttribute('aria-labelledby')).toBeTruthy();
    expect(screen.getByText('task.block.title')).toBeTruthy();
  });
});
