// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { SessionContext } from '../api/authApi';
import { SignedInPanel } from './SignedInPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));
vi.mock('../hooks/useAuth', () => ({
  useLogout: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock('./SessionsPanel', () => ({ SessionsPanel: () => <div>sessions-panel</div> }));
vi.mock('./SessionAdministrationPanel', () => ({
  SessionAdministrationPanel: () => <div>administration-panel</div>,
}));
vi.mock('./ChangePasswordPanel', () => ({
  ChangePasswordPanel: () => <div>change-password-panel</div>,
}));

function session(overrides: Partial<SessionContext> = {}): SessionContext {
  return {
    userId: '3f1a0d7e-0000-4000-8000-000000000001',
    email: 'elena@atelier.ro',
    accountState: 'ACTIVE',
    permissions: ['SESSION_VIEW_ANY'],
    landingTarget: 'TRIAGE',
    serverTime: '2026-08-20T09:00:00Z',
    ...overrides,
  } as SessionContext;
}

describe('Account', () => {
  afterEach(cleanup);

  it('offers the things a person does to their own access', () => {
    render(<SignedInPanel session={session()} />);

    expect(screen.getByText('sessions-panel')).toBeDefined();
    expect(screen.getByText('change-password-panel')).toBeDefined();
    expect(screen.getByRole('button', { name: 'auth.logout.submit' })).toBeDefined();
  });

  it('shows session administration only to someone who may act on other people', () => {
    render(<SignedInPanel session={session()} />);

    expect(screen.getByText('administration-panel')).toBeDefined();
  });

  it('withholds it from someone who may not', () => {
    render(<SignedInPanel session={session({ permissions: ['SESSION_VIEW_OWN'] })} />);

    expect(screen.queryByText('administration-panel')).toBeNull();
    expect(screen.getByText('sessions-panel')).toBeDefined();
  });

  it('shows no landing target and no permission count', () => {
    render(<SignedInPanel session={session()} />);

    expect(screen.queryByText(/TRIAGE/)).toBeNull();
    expect(screen.queryByText('auth.signedIn.permissions')).toBeNull();
  });
});
