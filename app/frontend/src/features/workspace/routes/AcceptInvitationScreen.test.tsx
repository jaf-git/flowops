// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { JSX } from 'react';

import { ApiError } from '../../../shared/api/client';
import type { InvitationPreview } from '../api/invitationApi';
import { AcceptInvitationScreen } from './AcceptInvitationScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let invitation: {
  isPending: boolean;
  isError: boolean;
  data: InvitationPreview | undefined;
  error: Error | null;
  refetch?: () => unknown;
};

function refused(): ApiError {
  return new ApiError(410, { code: 'INVITATION_NOT_USABLE', message: 'gone' });
}

vi.mock('../hooks/useInvitation', () => ({
  useInvitation: () => invitation,
}));

vi.mock('../hooks/useJoinWorkspace', () => ({
  useAcceptInvitation: () => ({ mutate: vi.fn(), isPending: false, isSuccess: false, error: null }),
  useDeclineInvitation: () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    error: null,
  }),
}));

function renderScreen(element: JSX.Element): void {
  render(<MemoryRouter initialEntries={['/en/invitation?token=a-token']}>{element}</MemoryRouter>);
}

function preview(overrides: Partial<InvitationPreview> = {}): InvitationPreview {
  return {
    workspaceName: 'Atelier București',
    role: 'EMPLOYEE',
    manager: { displayName: 'Ioana Radu', reassigned: false },
    inviter: { displayName: 'Maria Enache' },
    consent: { version: 'a3f2c1e09b4d', text: 'What FlowOps records about you.' },
    ...overrides,
  };
}

beforeEach(() => {
  invitation = { isPending: false, isError: false, data: undefined, error: null, refetch: vi.fn() };
});

afterEach(cleanup);

describe('opening an invitation', () => {
  it('says it is opening the invitation before it has one', () => {
    invitation = { isPending: true, isError: false, data: undefined, error: null };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    expect(screen.getByText('workspace.invitation.loading')).toBeTruthy();
  });

  it('shows the workspace, the role, the manager and who invited them', () => {
    invitation = { isPending: false, isError: false, data: preview(), error: null };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    expect(screen.getByText('workspace.invitation.intro')).toBeTruthy();
    expect(screen.getByText('Ioana Radu')).toBeTruthy();
    expect(screen.getByText('workspace.invitation.role.EMPLOYEE')).toBeTruthy();
  });

  it('shows the consent text in full and names its version', () => {
    invitation = { isPending: false, isError: false, data: preview(), error: null };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    const region = screen.getByRole('region', { name: 'workspace.invitation.consent.regionLabel' });
    expect(region.textContent).toContain('What FlowOps records about you.');
    expect(region.getAttribute('tabindex')).toBe('0');
    expect(screen.getByText('workspace.invitation.consent.version')).toBeTruthy();
  });

  it('says so when the manager shown is not the one named in the invitation', () => {
    invitation = {
      isPending: false,
      isError: false,
      data: preview({ manager: { displayName: 'Ionuț Petrescu', reassigned: true } }),
      error: null,
    };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    expect(screen.getByText('workspace.invitation.reassigned')).toBeTruthy();
  });

  it('says nothing about re-parenting when the manager is the one who was named', () => {
    invitation = { isPending: false, isError: false, data: preview(), error: null };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    expect(screen.queryByText('workspace.invitation.reassigned')).toBeNull();
  });

  it('says a link with no token cannot be used, rather than spinning for ever', () => {
    invitation = { isPending: true, isError: false, data: undefined, error: null };

    renderScreen(<AcceptInvitationScreen token="" />);

    expect(screen.getByText('workspace.invitation.unusable.heading')).toBeTruthy();
    expect(screen.queryByText('workspace.invitation.loading')).toBeNull();
  });

  it('gives one message for a link that cannot be used, and does not say why', () => {
    invitation = { isPending: false, isError: true, data: undefined, error: refused() };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    expect(screen.getByText('workspace.invitation.unusable.heading')).toBeTruthy();
    expect(screen.queryByText('workspace.invitation.heading')).toBeNull();
    expect(screen.queryByRole('region')).toBeNull();
  });

  it.each([
    ['the connection dropped', new Error('Failed to fetch'), true],
    ['the server broke', new ApiError(500, { code: 'INTERNAL', message: 'boom' }), true],
    ['nothing arrived at all', null, false],
  ])('does not call the invitation unusable when %s', (_case, failure, errored) => {
    invitation = { isPending: false, isError: errored, data: undefined, error: failure };

    renderScreen(<AcceptInvitationScreen token="a-token" />);

    expect(screen.getByText('workspace.invitation.unchecked.heading')).toBeTruthy();
    expect(screen.queryByText('workspace.invitation.unusable.heading')).toBeNull();
    expect(screen.getByText('workspace.invitation.unchecked.retry')).toBeTruthy();
  });

  it('asks again when the person asks it to', () => {
    invitation = {
      isPending: false,
      isError: true,
      data: undefined,
      error: new Error('offline'),
      refetch: vi.fn(),
    };

    renderScreen(<AcceptInvitationScreen token="a-token" />);
    fireEvent.click(screen.getByText('workspace.invitation.unchecked.retry'));

    expect(invitation.refetch).toHaveBeenCalled();
  });
});
