// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { ChangePasswordPanel } from './ChangePasswordPanel';

const reauthenticate = vi.fn();
const changePassword = vi.fn();
const resetReauthenticate = vi.fn();
const resetChangePassword = vi.fn();

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),

  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

vi.mock('../hooks/useAuth', () => ({
  useReauthenticate: () => ({
    mutate: reauthenticate,
    reset: resetReauthenticate,
    isPending: false,
    isError: false,
    error: null,
  }),
  useChangePassword: () => ({
    mutate: changePassword,
    reset: resetChangePassword,
    isPending: false,
    isError: false,
    error: null,
  }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function confirmWith(outcome: 'success' | { status: number }): Promise<void> {
  reauthenticate.mockImplementation((_password: string, handlers: { onSuccess?: () => void }) => {
    if (outcome === 'success') {
      handlers.onSuccess?.();
    }
  });
  await userEvent.type(screen.getByLabelText('auth.reauthenticate.currentPassword'), 'a password');
  await userEvent.click(screen.getByRole('button'));
}

describe('the change password panel', () => {
  it('asks for the current password before offering the new one', () => {
    render(<ChangePasswordPanel />);

    expect(screen.queryByLabelText('auth.reauthenticate.currentPassword')).not.toBeNull();
    expect(screen.queryByLabelText('auth.changePassword.newPassword')).toBeNull();
  });

  it('offers the new password only once the challenge is answered', async () => {
    render(<ChangePasswordPanel />);

    await confirmWith('success');

    expect(screen.queryByLabelText('auth.changePassword.newPassword')).not.toBeNull();
  });

  it('returns to the challenge when the window has closed', async () => {
    render(<ChangePasswordPanel />);
    await confirmWith('success');

    changePassword.mockImplementation(
      (_password: string, handlers: { onError?: (failure: Error) => void }) => {
        handlers.onError?.(new ApiError(403, { code: 'REAUTHENTICATION_REQUIRED', details: [] }));
      },
    );
    await userEvent.type(
      screen.getByLabelText('auth.changePassword.newPassword'),
      'a new password',
    );
    await userEvent.click(screen.getByRole('button'));

    expect(screen.queryByLabelText('auth.reauthenticate.currentPassword')).not.toBeNull();
    expect(screen.queryByLabelText('auth.changePassword.newPassword')).toBeNull();
  });

  it('stays on the new password when the policy refuses it', async () => {
    render(<ChangePasswordPanel />);
    await confirmWith('success');

    changePassword.mockImplementation(
      (_password: string, handlers: { onError?: (failure: Error) => void }) => {
        handlers.onError?.(
          new ApiError(400, {
            code: 'PASSWORD_POLICY_VIOLATION',
            details: [{ field: 'password', rule: 'MINIMUM_LENGTH' }],
          }),
        );
      },
    );
    await userEvent.type(screen.getByLabelText('auth.changePassword.newPassword'), 'short');
    await userEvent.click(screen.getByRole('button'));

    expect(screen.queryByLabelText('auth.changePassword.newPassword')).not.toBeNull();
  });

  it('returns to the challenge after a successful change, because the elevation did not survive it', async () => {
    render(<ChangePasswordPanel />);
    await confirmWith('success');

    changePassword.mockImplementation((_password: string, handlers: { onSuccess?: () => void }) => {
      handlers.onSuccess?.();
    });
    await userEvent.type(
      screen.getByLabelText('auth.changePassword.newPassword'),
      'a new password',
    );
    await userEvent.click(screen.getByRole('button'));

    expect(screen.queryByLabelText('auth.reauthenticate.currentPassword')).not.toBeNull();
    expect(screen.queryByText('auth.changePassword.changed')).not.toBeNull();
  });
});
