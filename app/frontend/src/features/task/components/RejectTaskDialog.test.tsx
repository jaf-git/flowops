// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { RejectTaskDialog } from './RejectTaskDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
const reset = vi.fn();
let state: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useTasks', () => ({
  useRejectTask: () => ({ ...state, mutate, reset }),
}));

beforeEach(() => {
  mutate.mockReset();
  reset.mockReset();
  state = { isPending: false, error: undefined };
});

afterEach(cleanup);

function open() {
  return render(<RejectTaskDialog taskId="a-task" onClose={vi.fn()} onRejected={vi.fn()} />);
}

describe('declining an assignment', () => {
  it('will not send a rejection with nothing in it', () => {
    open();

    expect(
      (screen.getByRole('button', { name: 'task.reject.submit' }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('lets it go once a reason has been written', () => {
    open();

    fireEvent.change(screen.getByLabelText('task.reject.field.reason'), {
      target: { value: 'This is Cristina account, not mine' },
    });

    expect(
      (screen.getByRole('button', { name: 'task.reject.submit' }) as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('tells the person when the server refuses', () => {
    state = {
      isPending: false,
      error: new ApiError(409, { code: 'ILLEGAL_TRANSITION', message: 'refused' }),
    };
    open();

    expect(screen.getByText('task.reject.error.ILLEGAL_TRANSITION')).toBeTruthy();
  });

  it('still says something for a code it does not know', () => {
    state = {
      isPending: false,
      error: new ApiError(500, { code: 'SOMETHING_ELSE', message: 'refused' }),
    };
    open();

    expect(screen.getByText('task.reject.error.SOMETHING_ELSE')).toBeTruthy();
  });

  it('disables itself while the request is in flight', () => {
    state = { isPending: true, error: undefined };
    open();

    expect(
      (screen.getByRole('button', { name: 'task.reject.submitting' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });
});
