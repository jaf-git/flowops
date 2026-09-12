// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { ProposeDeadlineDialog } from './ProposeDeadlineDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
const reset = vi.fn();
let state: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useTasks', () => ({
  useProposeDeadline: () => ({ ...state, mutate, reset }),
}));

beforeEach(() => {
  mutate.mockReset();
  reset.mockReset();
  state = { isPending: false, error: undefined };
});

afterEach(cleanup);

function open() {
  return render(<ProposeDeadlineDialog taskId="a-task" onClose={vi.fn()} onProposed={vi.fn()} />);
}

function fill(field: string, value: string): void {
  fireEvent.change(screen.getByLabelText(field), { target: { value } });
}

describe('asking for a different date', () => {
  it('will not send with a date and no reason', () => {
    open();

    fill('task.proposeDeadline.field.date', '2026-09-15T15:00');

    expect(
      (screen.getByRole('button', { name: 'task.proposeDeadline.submit' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it('will not send with a reason and no date', () => {
    open();

    fill('task.proposeDeadline.field.reason', 'The parts arrive Friday');

    expect(
      (screen.getByRole('button', { name: 'task.proposeDeadline.submit' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it('sends once both have been given', () => {
    open();

    fill('task.proposeDeadline.field.date', '2026-09-15T15:00');
    fill('task.proposeDeadline.field.reason', 'The parts arrive Friday');

    expect(
      (screen.getByRole('button', { name: 'task.proposeDeadline.submit' }) as HTMLButtonElement)
        .disabled,
    ).toBe(false);
  });

  it('says the task stays with them while they wait for an answer', () => {
    open();

    expect(screen.getByText('task.proposeDeadline.note')).toBeTruthy();
  });

  it('tells the person when a proposal is already open', () => {
    state = {
      isPending: false,
      error: new ApiError(409, { code: 'PROPOSAL_ALREADY_OPEN', message: 'refused' }),
    };
    open();

    expect(screen.getByText('task.proposeDeadline.error.PROPOSAL_ALREADY_OPEN')).toBeTruthy();
  });

  it('disables itself while the request is in flight', () => {
    state = { isPending: true, error: undefined };
    open();

    expect(
      (screen.getByRole('button', { name: 'task.proposeDeadline.submitting' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });
});
