// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { SessionAdministrationPanel } from './SessionAdministrationPanel';

const reauthenticate = vi.fn();
const resetReauthenticate = vi.fn();
const terminate = vi.fn();
const resetTerminate = vi.fn();
const sessions = vi.fn();

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
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
  useTerminateSession: () => ({
    mutate: terminate,
    reset: resetTerminate,
    isPending: false,
    isError: false,
    error: null,
    variables: undefined,
  }),
  useUserSessions: () => sessions(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function withOneSessionFound(): void {
  sessions.mockReturnValue({
    data: [
      {
        reference: 'a-reference',
        current: false,
        createdAt: '2026-08-03T08:00:00Z',
        lastActiveAt: '2026-08-03T09:00:00Z',
        ipAddress: '203.0.113.10',
        deviceSummary: 'Chrome on Windows',
        coarseLocation: 'unknown',
      },
    ],
    isError: false,
    error: null,
  });
}

async function lookUpAPerson(): Promise<void> {
  await userEvent.type(
    screen.getByLabelText('auth.sessionAdministration.userId'),
    '11111111-1111-1111-1111-111111111111',
  );
  await userEvent.click(screen.getByRole('button', { name: 'auth.sessionAdministration.look' }));
}

async function confirmThePassword(): Promise<void> {
  reauthenticate.mockImplementation((_password: string, handlers: { onSuccess?: () => void }) => {
    handlers.onSuccess?.();
  });
  await userEvent.type(screen.getByLabelText('auth.reauthenticate.currentPassword'), 'a password');
  await userEvent.click(screen.getByRole('button', { name: 'auth.reauthenticate.submit' }));
}

describe('the session administration panel', () => {
  it('lists nobody until a person is asked for', () => {
    sessions.mockReturnValue({ data: undefined, isError: false, error: null });

    render(<SessionAdministrationPanel />);

    expect(screen.queryByText('auth.sessions.none')).toBeNull();
    expect(screen.queryByLabelText('auth.reauthenticate.currentPassword')).toBeNull();
  });

  it('shows the sessions but offers no way to end one until the password is confirmed', async () => {
    withOneSessionFound();
    render(<SessionAdministrationPanel />);

    await lookUpAPerson();

    expect(screen.queryByText('Chrome on Windows')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'auth.sessions.terminate' })).toBeNull();
    expect(screen.queryByLabelText('auth.reauthenticate.currentPassword')).not.toBeNull();
  });

  it('offers termination once the password is confirmed', async () => {
    withOneSessionFound();
    render(<SessionAdministrationPanel />);

    await lookUpAPerson();
    await confirmThePassword();

    expect(screen.queryByRole('button', { name: 'auth.sessions.terminate' })).not.toBeNull();
  });

  it('returns to the challenge when the window closed between confirming and clicking', async () => {
    withOneSessionFound();
    render(<SessionAdministrationPanel />);
    await lookUpAPerson();
    await confirmThePassword();

    terminate.mockImplementation(
      (_reference: string, handlers: { onError?: (failure: Error) => void }) => {
        handlers.onError?.(new ApiError(403, { code: 'REAUTHENTICATION_REQUIRED' }));
      },
    );
    await userEvent.click(screen.getByRole('button', { name: 'auth.sessions.terminate' }));

    expect(screen.queryByLabelText('auth.reauthenticate.currentPassword')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'auth.sessions.terminate' })).toBeNull();
  });

  it('does not return to the challenge when the refusal is about permission', async () => {
    withOneSessionFound();
    render(<SessionAdministrationPanel />);
    await lookUpAPerson();
    await confirmThePassword();

    terminate.mockImplementation(
      (_reference: string, handlers: { onError?: (failure: Error) => void }) => {
        handlers.onError?.(new ApiError(403, { code: 'PERMISSION_DENIED' }));
      },
    );
    await userEvent.click(screen.getByRole('button', { name: 'auth.sessions.terminate' }));

    expect(screen.queryByRole('button', { name: 'auth.sessions.terminate' })).not.toBeNull();
  });

  it('confirms when a session has been ended', async () => {
    withOneSessionFound();
    render(<SessionAdministrationPanel />);
    await lookUpAPerson();
    await confirmThePassword();

    terminate.mockImplementation((_reference: string, handlers: { onSuccess?: () => void }) => {
      handlers.onSuccess?.();
    });
    await userEvent.click(screen.getByRole('button', { name: 'auth.sessions.terminate' }));

    expect(screen.queryByText('auth.sessionAdministration.terminated')).not.toBeNull();
  });
});
