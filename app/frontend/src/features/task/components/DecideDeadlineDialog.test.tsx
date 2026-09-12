// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { DecideDeadlineDialog } from './DecideDeadlineDialog';
import type { DeadlineProposal } from '../api/taskApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
const reset = vi.fn();
let state: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useTasks', () => ({
  useDecideDeadline: () => ({ ...state, mutate, reset }),
}));

beforeEach(() => {
  mutate.mockReset();
  reset.mockReset();
  state = { isPending: false, error: undefined };
});

afterEach(cleanup);

const PROPOSAL: DeadlineProposal = {
  proposedDeadline: '2026-09-15T15:00:00Z',
  reason: 'The parts arrive Friday',
  proposerId: 'andrei',
  proposedAt: '2026-08-11T09:00:00Z',
};

function open() {
  return render(
    <DecideDeadlineDialog
      taskId="a-task"
      proposal={PROPOSAL}
      onClose={vi.fn()}
      onDecided={vi.fn()}
    />,
  );
}

describe('answering a date request', () => {
  it('shows what was asked for and why, before either answer', () => {
    open();

    expect(screen.getByText('The parts arrive Friday')).toBeTruthy();
  });

  it('offers no reason field while the answer is agreement', () => {
    open();

    expect(screen.queryByLabelText('task.decideDeadline.field.reason')).toBeNull();
  });

  it('asks for a reason the moment the answer becomes a refusal', () => {
    open();

    fireEvent.click(screen.getByLabelText('task.decideDeadline.decline'));

    expect(screen.getByLabelText('task.decideDeadline.field.reason')).toBeTruthy();
  });

  it('lets agreement go without anything written', () => {
    open();

    expect(
      (screen.getByRole('button', { name: 'task.decideDeadline.submit' }) as HTMLButtonElement)
        .disabled,
    ).toBe(false);
  });

  it('will not let a refusal go in silence', () => {
    open();

    fireEvent.click(screen.getByLabelText('task.decideDeadline.decline'));

    expect(
      (screen.getByRole('button', { name: 'task.decideDeadline.submit' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it('lets a refusal go once a reason has been written', () => {
    open();

    fireEvent.click(screen.getByLabelText('task.decideDeadline.decline'));
    fireEvent.change(screen.getByLabelText('task.decideDeadline.field.reason'), {
      target: { value: 'The client will not move the audit' },
    });

    expect(
      (screen.getByRole('button', { name: 'task.decideDeadline.submit' }) as HTMLButtonElement)
        .disabled,
    ).toBe(false);
  });

  it('tells the person when the date they are answering has gone stale', () => {
    state = {
      isPending: false,
      error: new ApiError(422, { code: 'PROPOSAL_IS_STALE', message: 'refused' }),
    };
    open();

    expect(screen.getByText('task.decideDeadline.error.PROPOSAL_IS_STALE')).toBeTruthy();
  });

  it('disables itself while the request is in flight', () => {
    state = { isPending: true, error: undefined };
    open();

    expect(
      (screen.getByRole('button', { name: 'task.decideDeadline.submitting' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });
});
