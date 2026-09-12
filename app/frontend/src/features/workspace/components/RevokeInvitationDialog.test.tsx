// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { PendingInvitation } from '../api/workspaceApi';
import { RevokeInvitationDialog } from './RevokeInvitationDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
let revokeState: { isPending: boolean; isError: boolean; error: Error | null } = {
  isPending: false,
  isError: false,
  error: null,
};

vi.mock('../hooks/usePeople', () => ({
  useRevokeInvitation: () => ({ ...revokeState, mutate, reset: vi.fn() }),
}));

const INVITATION: PendingInvitation = {
  id: 'inv-1',
  emailAddress: 'ionut@atelier.ro',
  intendedRole: 'EMPLOYEE',
  intendedManagerId: 'maria',
  inviterId: 'maria',
  state: 'SENT',
  expiresAt: '2026-08-11T09:00:00Z',
};

beforeEach(() => {
  mutate.mockReset();
  revokeState = { isPending: false, isError: false, error: null };
});

afterEach(cleanup);

describe('withdrawing an invitation', () => {
  it('stays mounted and closed until an invitation is chosen, so focus can be returned', () => {
    const { container } = render(
      <RevokeInvitationDialog invitation={undefined} onClose={vi.fn()} onRevoked={vi.fn()} />,
    );

    const dialog = container.querySelector('dialog');
    expect(dialog).not.toBeNull();
    expect(dialog?.hasAttribute('open')).toBe(false);
  });

  it('states what withdrawing will do before anybody confirms it', () => {
    render(
      <RevokeInvitationDialog invitation={INVITATION} onClose={vi.fn()} onRevoked={vi.fn()} />,
    );

    expect(screen.getByText('workspace.revoke.heading')).toBeTruthy();
    expect(screen.getByText('workspace.revoke.consequence.linkStops')).toBeTruthy();
  });

  it('withdraws when confirmed, and hands back what the server actually did', () => {
    const onRevoked = vi.fn();
    const onClose = vi.fn();
    const result = {
      id: 'inv-1',
      emailAddress: 'ionut@atelier.ro',
      state: 'REVOKED',
      revokedAt: '2026-08-04T09:00:00Z',
    };
    mutate.mockImplementation(
      (_id: string, options: { onSuccess: (value: typeof result) => void }) =>
        options.onSuccess(result),
    );

    render(
      <RevokeInvitationDialog invitation={INVITATION} onClose={onClose} onRevoked={onRevoked} />,
    );
    fireEvent.click(screen.getByText('workspace.revoke.confirm'));

    expect(mutate).toHaveBeenCalledWith('inv-1', expect.anything());
    expect(onRevoked).toHaveBeenCalledWith(result);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("names who accepted, in this screen's language rather than the server's", () => {
    revokeState = {
      isPending: false,
      isError: true,
      error: new ApiError(409, {
        code: 'ALREADY_ACCEPTED',
        message: 'that invitation has been accepted and the person is now a member',
        details: [{ field: 'member', rule: 'Ionuț Petrescu' }],
      }),
    };

    render(
      <RevokeInvitationDialog invitation={INVITATION} onClose={vi.fn()} onRevoked={vi.fn()} />,
    );

    expect(screen.getByText('workspace.revoke.error.ALREADY_ACCEPTED')).toBeTruthy();
    expect(screen.queryByText(/accepted this invitation and is now a member/)).toBeNull();
    expect(screen.queryByText('workspace.revoke.confirm')).toBeNull();
    expect(screen.queryByText('workspace.revoke.heading')).toBeNull();
  });

  it('reports an unexpected failure without pretending to know the cause', () => {
    revokeState = { isPending: false, isError: true, error: new Error('the network went away') };

    render(
      <RevokeInvitationDialog invitation={INVITATION} onClose={vi.fn()} onRevoked={vi.fn()} />,
    );

    expect(screen.getByText('workspace.revoke.error.unexpected')).toBeTruthy();
    expect(screen.getByText('workspace.revoke.confirm')).toBeTruthy();
  });

  it('disables the confirmation while it is in flight', () => {
    revokeState = { isPending: true, isError: false, error: null };

    render(
      <RevokeInvitationDialog invitation={INVITATION} onClose={vi.fn()} onRevoked={vi.fn()} />,
    );

    expect(screen.getByText('workspace.revoke.withdrawing').closest('button')?.disabled).toBe(true);
  });
});
