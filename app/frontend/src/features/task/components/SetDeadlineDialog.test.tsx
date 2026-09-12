// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { SetDeadlineDialog } from './SetDeadlineDialog';

const setMutate = vi.fn();
const setReset = vi.fn();
const onClose = vi.fn();
const onSet = vi.fn();
let setState: { isPending: boolean; error: unknown } = { isPending: false, error: null };

vi.mock('../hooks/useTasks', () => ({
  useSetDeadline: () => ({ ...setState, mutate: setMutate, reset: setReset }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function open(currentDeadline: string | null = null) {
  return render(
    <SetDeadlineDialog
      taskId="task-1"
      currentDeadline={currentDeadline}
      onClose={onClose}
      onSet={onSet}
    />,
  );
}

function theDateField(): HTMLInputElement {
  return document.getElementById('set-deadline-date') as HTMLInputElement;
}

afterEach(() => {
  cleanup();
  [setMutate, setReset, onClose, onSet].forEach((fn) => fn.mockReset());
  setState = { isPending: false, error: null };
});

describe('the set-deadline dialog', () => {
  it('asks for a date and nothing else — no reason, because nobody is being asked', () => {
    open();

    expect(theDateField()).toBeTruthy();
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('starts from the date already chosen when there is one', () => {
    open('2026-09-15T15:00:00Z');

    expect(theDateField().value).toBe('2026-09-15T15:00');
  });

  it('starts blank on work nobody has dated', () => {
    open();

    expect(theDateField().value).toBe('');
  });

  it('will not submit an empty date', () => {
    open();

    const confirm = screen
      .getByText('task.setDeadline.confirm')
      .closest('button') as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
  });

  it('sends the chosen date as an instant, for this task', () => {
    open();

    fireEvent.change(theDateField(), { target: { value: '2026-09-15T15:00' } });
    fireEvent.click(screen.getByText('task.setDeadline.confirm'));

    expect(setMutate).toHaveBeenCalledTimes(1);
    const [payload] = setMutate.mock.calls[0] as [{ id: string; deadline: string }];
    expect(payload.id).toBe('task-1');
    expect(payload.deadline).toBe(new Date('2026-09-15T15:00').toISOString());
  });

  it('renders the refusal the server actually gave', () => {
    setState = { isPending: false, error: new ApiError(409, { code: 'USE_A_PROPOSAL_INSTEAD' }) };

    open();

    expect(screen.getByText('task.setDeadline.error.USE_A_PROPOSAL_INSTEAD')).toBeTruthy();
  });

  it('clears its own error state when it is closed, so reopening is not haunted', () => {
    open();

    fireEvent.click(screen.getByText('task.setDeadline.cancel'));

    expect(setReset).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });
});
