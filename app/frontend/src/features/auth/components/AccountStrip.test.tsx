// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { SessionContext } from '../api/authApi';
import { AccountStrip } from './AccountStrip';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const logout = vi.fn();
vi.mock('../hooks/useAuth', () => ({
  useLogout: () => ({ mutate: logout, isPending: false }),
}));
vi.mock('./SessionsPanel', () => ({ SessionsPanel: () => <div>sessions-panel</div> }));
vi.mock('./ChangePasswordPanel', () => ({
  ChangePasswordPanel: () => <div>change-password-panel</div>,
}));

function session(): SessionContext {
  return {
    userId: '3f1a0d7e-0000-4000-8000-000000000001',
    email: 'elena@atelier.ro',
    accountState: 'ACTIVE',
    permissions: ['SESSION_VIEW_OWN'],
    landingTarget: 'WORKSPACE_SETUP',
    serverTime: '2026-08-20T09:00:00Z',
  } as SessionContext;
}

describe('the account strip on the setup screen', () => {
  afterEach(() => {
    cleanup();
    logout.mockReset();
  });

  it('offers sign-out without anything having to be opened first', () => {
    render(<AccountStrip session={session()} />);

    fireEvent.click(screen.getByRole('button', { name: 'auth.logout.submit' }));

    expect(logout).toHaveBeenCalled();
  });

  it('reaches sessions and the password in one click', () => {
    render(<AccountStrip session={session()} />);
    expect(screen.queryByText('sessions-panel')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'auth.account.manage' }));

    expect(screen.getByText('sessions-panel')).toBeDefined();
    expect(screen.getByText('change-password-panel')).toBeDefined();
  });

  it('says who is signed in, so somebody who used the wrong address can see that they did', () => {
    render(<AccountStrip session={session()} />);

    expect(screen.getByText('auth.signedIn.as')).toBeDefined();
  });

  it('reports whether it is open', () => {
    render(<AccountStrip session={session()} />);
    const toggle = screen.getByRole('button', { name: 'auth.account.manage' });

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
  });
});
