// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { ResetPasswordScreen } from './ResetPasswordScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let link: {
  isPending: boolean;
  isError: boolean;
  error: Error | null;
  refetch: () => Promise<unknown>;
};
const completeReset = {
  mutate: vi.fn(),
  isPending: false,
  isSuccess: false,
  isError: false,
  error: null as Error | null,
};

vi.mock('../hooks/useAuth', () => ({
  useResetToken: () => link,
  useCompletePasswordReset: () => completeReset,
}));

beforeEach(() => {
  link = { isPending: false, isError: false, error: null, refetch: vi.fn() };
  completeReset.mutate = vi.fn();
  completeReset.isPending = false;
  completeReset.isSuccess = false;
  completeReset.isError = false;
  completeReset.error = null;
});

afterEach(cleanup);

function renderScreen(token: string) {
  return render(
    <MemoryRouter initialEntries={[`/en/reset-password?token=${token}`]}>
      <ResetPasswordScreen token={token} />
    </MemoryRouter>,
  );
}

function policyRefusal(rule: string): ApiError {
  return new ApiError(400, {
    code: 'PASSWORD_POLICY_VIOLATION',
    message: 'refused',
    details: [{ field: 'password', rule }],
  });
}

function deadLink(): ApiError {
  return new ApiError(410, { code: 'RESET_TOKEN_UNUSABLE', message: 'gone' });
}

describe('the reset password screen', () => {
  it('shows the password form once the link has been checked', () => {
    renderScreen('a-good-token');

    expect(screen.getByLabelText(/auth.field.password/)).toBeDefined();
    expect(screen.queryByText('auth.resetPassword.dead')).toBeNull();
  });

  it('says it is checking the link before it knows', () => {
    link = { isPending: true, isError: false, error: null, refetch: vi.fn() };

    renderScreen('a-good-token');

    expect(screen.getByText('auth.resetPassword.checking')).toBeDefined();
    expect(screen.queryByLabelText(/auth.field.password/)).toBeNull();
  });

  it.each([
    ['a link the server refused', 'a-dead-token', true],
    ['a link with no token at all', '', false],
  ])('offers one message and one way forward for %s', (_case, token, serverRefused) => {
    link = {
      isPending: false,
      isError: serverRefused,
      error: serverRefused ? deadLink() : null,
      refetch: vi.fn(),
    };

    renderScreen(token);

    expect(screen.getByText('auth.resetPassword.dead')).toBeDefined();
    expect(screen.getByText('auth.resetPassword.askAgain')).toBeDefined();
    expect(screen.queryByLabelText(/auth.field.password/)).toBeNull();
  });

  it('submits the token it was given with the password that was typed', () => {
    renderScreen('a-good-token');

    fireEvent.change(screen.getByLabelText(/auth.field.password/), {
      target: { value: 'corect-cal-baterie-capsator' },
    });
    fireEvent.submit(screen.getByText('auth.resetPassword.submit').closest('form')!);

    expect(completeReset.mutate).toHaveBeenCalledWith({
      token: 'a-good-token',
      newPassword: 'corect-cal-baterie-capsator',
    });
  });

  it('names the unchanged-password rule against the field and leaves the form usable', () => {
    completeReset.isError = true;
    completeReset.error = policyRefusal('NOT_CURRENT_PASSWORD');

    renderScreen('a-good-token');

    expect(screen.getByText('auth.error.passwordRule.NOT_CURRENT_PASSWORD')).toBeDefined();
    expect(screen.getByLabelText(/auth.field.password/)).toBeDefined();
    expect(screen.queryByText('auth.resetPassword.dead')).toBeNull();
  });

  it('does not repeat a length rule as a field error, because the checklist already shows it', () => {
    completeReset.isError = true;
    completeReset.error = policyRefusal('MINIMUM_LENGTH');

    renderScreen('a-good-token');

    expect(screen.getAllByText('auth.error.passwordRule.MINIMUM_LENGTH')).toHaveLength(1);
    expect(screen.getByLabelText(/auth.field.password/)).toBeDefined();
  });

  it('says the link is dead when the submission is the thing that finds out', () => {
    completeReset.isError = true;
    completeReset.error = deadLink();

    renderScreen('a-good-token');

    expect(screen.getByRole('alert').textContent).toBe('auth.resetPassword.dead');
  });

  it('sends the person to sign in afterwards, and says every session ended', () => {
    completeReset.isSuccess = true;

    renderScreen('a-good-token');

    expect(screen.getByText('auth.resetPassword.done')).toBeDefined();
    expect(screen.getByText('auth.resetPassword.signIn')).toBeDefined();
    expect(screen.queryByLabelText(/auth.field.password/)).toBeNull();
  });

  it('keeps saying the password is set after the link it validated has been spent', () => {
    completeReset.isSuccess = true;
    link = { isPending: false, isError: true, error: deadLink(), refetch: vi.fn() };

    renderScreen('a-good-token');

    expect(screen.getByText('auth.resetPassword.done')).toBeDefined();
    expect(screen.queryByText('auth.resetPassword.dead')).toBeNull();
  });

  it.each([
    ['the server broke', new ApiError(500, { code: 'INTERNAL', message: 'boom' })],
    ['the connection dropped', new Error('Failed to fetch')],
  ])('does not call the link dead when %s', (_case, failure) => {
    link = { isPending: false, isError: true, error: failure, refetch: vi.fn() };

    renderScreen('a-good-token');

    expect(screen.getByText('auth.resetPassword.unchecked')).toBeDefined();
    expect(screen.queryByText('auth.resetPassword.dead')).toBeNull();
  });

  it('asks the link again when the person asks it to', () => {
    link = { isPending: false, isError: true, error: new Error('offline'), refetch: vi.fn() };

    renderScreen('a-good-token');
    fireEvent.click(screen.getByText('auth.resetPassword.retry'));

    expect(link.refetch).toHaveBeenCalled();
  });

  it('does not call the link dead when the save itself failed for another reason', () => {
    completeReset.isError = true;
    completeReset.error = new Error('Failed to fetch');

    renderScreen('a-good-token');

    expect(screen.getByRole('alert').textContent).toBe('auth.resetPassword.saveFailed');
  });
});
