// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
};

vi.mock('../hooks/useInvitation', () => ({
  useInvitation: () => invitation,
}));

const join = {
  mutate: vi.fn(),
  isPending: false,
  isSuccess: false,
  error: null as Error | null,
};
const decline = {
  mutate: vi.fn(),
  isPending: false,
  isSuccess: false,
  error: null as Error | null,
};

vi.mock('../hooks/useJoinWorkspace', () => ({
  useAcceptInvitation: () => join,
  useDeclineInvitation: () => decline,
}));

beforeEach(() => {
  invitation = {
    isPending: false,
    isError: false,
    data: {
      workspaceName: 'Atelier București',
      role: 'EMPLOYEE',
      manager: { displayName: 'Maria Enache', reassigned: false },
      inviter: { displayName: 'Maria Enache' },
      consent: { version: '1beddcbbb2f1', text: 'What FlowOps records about you.' },
    },
    error: null,
  };
  join.mutate = vi.fn();
  join.isPending = false;
  join.isSuccess = false;
  join.error = null;
  decline.mutate = vi.fn();
  decline.isPending = false;
  decline.isSuccess = false;
  decline.error = null;
});

afterEach(cleanup);

function renderScreen(): void {
  render(
    <MemoryRouter initialEntries={['/en/invitation?token=a-token']}>
      <AcceptInvitationScreen token="a-token" />
    </MemoryRouter>,
  );
}

describe('the join form', () => {
  it('will not let somebody agree before they have agreed', () => {
    renderScreen();

    expect(
      screen
        .getByRole('button', { name: 'workspace.invitation.join.submit' })
        .hasAttribute('disabled'),
    ).toBe(true);
  });

  it('lets them join once they have ticked the box, and sends the version they were shown', () => {
    renderScreen();

    fireEvent.change(screen.getByLabelText(/join.name.label/), {
      target: { value: 'Cosmin Ionescu' },
    });
    fireEvent.change(screen.getByLabelText(/join.password.label/), {
      target: { value: 'a-long-enough-passphrase' },
    });
    fireEvent.click(screen.getByLabelText(/join.agree/));
    fireEvent.click(screen.getByRole('button', { name: 'workspace.invitation.join.submit' }));

    expect(join.mutate).toHaveBeenCalledWith({
      displayName: 'Cosmin Ionescu',
      password: 'a-long-enough-passphrase',
      consentAccepted: true,
      consentVersion: '1beddcbbb2f1',
    });
  });

  it('names the password rule against the field rather than at the top of the form', () => {
    join.error = new ApiError(400, {
      code: 'PASSWORD_POLICY_VIOLATION',
      message: 'no',
      details: [{ field: 'password', rule: 'MINIMUM_LENGTH' }],
    });
    renderScreen();

    expect(screen.getAllByText('auth.error.passwordRule.MINIMUM_LENGTH').length).toBeGreaterThan(1);
    expect(screen.queryByText('workspace.invitation.join.error.UNKNOWN')).toBeNull();
  });

  it('tells somebody who already has an account the one thing they can act on', () => {
    join.error = new ApiError(409, { code: 'ALREADY_MEMBER', message: 'no', details: [] });
    renderScreen();

    expect(screen.getByText('workspace.invitation.join.error.ALREADY_MEMBER')).toBeTruthy();
  });

  it('says the words changed rather than recording an agreement to words nobody read', () => {
    join.error = new ApiError(409, { code: 'CONSENT_VERSION_STALE', message: 'no', details: [] });
    renderScreen();

    expect(screen.getByText('workspace.invitation.join.error.CONSENT_VERSION_STALE')).toBeTruthy();
  });

  it('offers declining, and declining is not an error', () => {
    renderScreen();

    fireEvent.click(screen.getByRole('button', { name: 'workspace.invitation.join.decline' }));

    expect(decline.mutate).toHaveBeenCalled();
  });

  it('confirms that nothing was created, and offers no way to reconsider', () => {
    decline.isSuccess = true;
    renderScreen();

    expect(screen.getByText(/declined.body/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'workspace.invitation.join.submit' })).toBeNull();
  });

  it('blocks both buttons while a request is in flight, so nothing is submitted twice', () => {
    join.isPending = true;
    renderScreen();

    expect(
      screen
        .getByRole('button', { name: 'workspace.invitation.join.decline' })
        .hasAttribute('disabled'),
    ).toBe(true);
  });
});
