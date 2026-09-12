// @vitest-environment jsdom

import { cleanup, fireEvent, render as renderBare, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { People } from '../api/workspaceApi';
import { PeopleScreen } from './PeopleScreen';
import { NoticeCentre } from '../../../shared/notice/NoticeCentre';
import { NoticeProvider } from '../../../shared/notice/NoticeProvider';

function render(ui: Parameters<typeof renderBare>[0]): ReturnType<typeof renderBare> {
  return renderBare(
    <NoticeProvider>
      {ui}
      <NoticeCentre label="notices" dismissLabel="dismiss" />
    </NoticeProvider>,
  );
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),

  Trans: ({ i18nKey }: { i18nKey: string }) => <span>{i18nKey}</span>,
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let peopleState: { isPending: boolean; isError: boolean; data: People | undefined } = {
  isPending: false,
  isError: false,
  data: undefined,
};

vi.mock('../components/RevokeInvitationDialog', () => ({
  RevokeInvitationDialog: ({
    invitation,
    onRevoked,
  }: {
    invitation?: { emailAddress: string };
    onRevoked: (result: { emailAddress: string; revokedAt: string | null }) => void;
  }) =>
    invitation === undefined ? null : (
      <button
        type="button"
        onClick={() =>
          onRevoked({ emailAddress: invitation.emailAddress, revokedAt: '2026-08-04T09:00:00Z' })
        }
      >
        confirm-withdrawal
      </button>
    ),
}));

vi.mock('../components/MoveReportingLineDialog', () => ({
  MoveReportingLineDialog: ({
    person,
    onMoved,
  }: {
    person?: { displayName: string };
    onMoved: (personName: string, managerName: string) => void;
  }) =>
    person === undefined ? null : (
      <button type="button" onClick={() => onMoved(person.displayName, 'Maria Ionescu')}>
        confirm-move
      </button>
    ),
}));

vi.mock('../hooks/useElevateSession', () => ({
  useElevateSession: () => ({ mutate: vi.fn(), reset: vi.fn(), isPending: false, error: null }),
}));

vi.mock('../hooks/usePeople', () => ({
  usePeople: () => peopleState,
  useErasurePreview: () => ({ data: undefined, isPending: true, isError: false }),
  useErasePerson: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
  useDeactivatePerson: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
  useRevokeInvitation: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
  useInvitePerson: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}));

const MARIA = {
  membershipId: 'maria',
  personId: 'p-maria',
  displayName: 'Maria Ionescu',
  role: 'OWNER',
  managerId: null,
  status: 'ACTIVE' as const,
  deactivatedAt: null,
  isSelf: true,
};

const IONUT = {
  membershipId: 'ionut',
  personId: 'p-ionut',
  displayName: 'Ionuț Petrescu',
  role: 'MANAGER',
  managerId: 'maria',
  status: 'ACTIVE' as const,
  deactivatedAt: null,
  isSelf: false,
};

const MARIA_SEEN_BY_ANOTHER = { ...MARIA, isSelf: false };

const IONUT_VIEWING = { ...IONUT, isSelf: true };

const ANDREI = {
  membershipId: 'andrei',
  personId: 'p-andrei',
  displayName: 'Andrei Munteanu',
  role: 'EMPLOYEE',
  managerId: 'maria',
  status: 'DEACTIVATED' as const,
  deactivatedAt: '2026-07-02T09:00:00Z',
  isSelf: false,
};

const PENDING = {
  id: 'inv-1',
  emailAddress: 'ionut@atelier.ro',
  intendedRole: 'EMPLOYEE' as const,
  intendedManagerId: 'maria',
  inviterId: 'maria',
  state: 'SENT',
  expiresAt: '2026-08-11T09:00:00Z',
};

function showing(data: People): void {
  peopleState = { isPending: false, isError: false, data };
}

afterEach(() => {
  cleanup();
  peopleState = { isPending: false, isError: false, data: undefined };
});

describe('the people screen', () => {
  it('shows every active member and who they report to', () => {
    showing({ people: [MARIA, IONUT], invitations: [], onlyMember: false });

    render(<PeopleScreen />);

    const tree = within(screen.getByRole('tree'));
    expect(tree.getByText('Maria Ionescu')).toBeTruthy();
    expect(tree.getByText('Ionuț Petrescu')).toBeTruthy();
  });

  it('tells the only member so, and offers the one action that changes it', () => {
    showing({ people: [MARIA], invitations: [], onlyMember: true });

    render(<PeopleScreen />);

    expect(screen.getByText('workspace.people.alone.heading')).toBeTruthy();
    expect(screen.queryByRole('tree')).toBeNull();
  });

  it('draws the tree once an invitation exists, even though the owner is still the only member', () => {
    showing({
      people: [MARIA],
      invitations: [
        {
          id: 'inv-1',
          emailAddress: 'ionut@atelier.ro',
          intendedRole: 'EMPLOYEE',
          intendedManagerId: 'maria',
          inviterId: 'maria',
          state: 'SENT',
          expiresAt: '2026-08-11T09:00:00Z',
        },
      ],
      onlyMember: true,
    });

    render(<PeopleScreen />);

    expect(screen.queryByText('workspace.people.alone.heading')).toBeNull();
    expect(screen.getByRole('tree')).toBeTruthy();
    expect(screen.getByText('ionut@atelier.ro')).toBeTruthy();
  });

  it('marks the viewer so they can find themselves', () => {
    showing({ people: [MARIA, IONUT], invitations: [], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.getByText('workspace.people.you')).toBeTruthy();
  });

  it('shows a pending invitation in the tree, marked as not yet joined', () => {
    showing({
      people: [MARIA],
      invitations: [
        {
          id: 'inv-1',
          emailAddress: 'stefan@atelier.ro',
          intendedRole: 'EMPLOYEE',
          intendedManagerId: 'maria',
          inviterId: 'maria',
          state: 'SENT',
          expiresAt: '2026-08-11T09:00:00Z',
        },
      ],
      onlyMember: false,
    });

    render(<PeopleScreen />);

    expect(screen.getByText('stefan@atelier.ro')).toBeTruthy();

    expect(screen.getByText('workspace.people.invitationState.SENT')).toBeTruthy();
  });

  it('renders nothing at all about invitations for a viewer who may not see them', () => {
    showing({ people: [MARIA, IONUT], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.queryByText('workspace.people.invitationState.SENT')).toBeNull();
    expect(screen.queryByText('workspace.people.invite')).toBeNull();
  });

  it('hides a deactivated person until the filter is turned on', () => {
    showing({ people: [MARIA, ANDREI], invitations: [], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.queryByText('Andrei Munteanu')).toBeNull();
    expect(screen.getByLabelText('workspace.people.showDeactivated')).toBeTruthy();
  });

  it('shows a deactivated person, with the date, once the filter is on', () => {
    showing({ people: [MARIA, IONUT, ANDREI], invitations: [], onlyMember: false });

    render(<PeopleScreen />);
    fireEvent.click(screen.getByLabelText('workspace.people.showDeactivated'));

    const tree = within(screen.getByRole('tree'));
    expect(tree.getByText('Andrei Munteanu')).toBeTruthy();
    expect(tree.getByText(/workspace\.people\.deactivatedOn/)).toBeTruthy();
  });

  it('does not say "it is just you" when somebody deactivated is there to be shown', () => {
    showing({ people: [MARIA, ANDREI], invitations: [], onlyMember: true });

    render(<PeopleScreen />);

    expect(screen.queryByText('workspace.people.alone.heading')).toBeNull();
    fireEvent.click(screen.getByLabelText('workspace.people.showDeactivated'));
    expect(within(screen.getByRole('tree')).getByText('Andrei Munteanu')).toBeTruthy();
  });

  it('does not offer the filter when nobody is deactivated', () => {
    showing({ people: [MARIA, IONUT], invitations: [], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.queryByLabelText('workspace.people.showDeactivated')).toBeNull();
  });

  it('offers Withdraw on a pending invitation, named for the address it withdraws', () => {
    showing({ people: [MARIA], invitations: [PENDING], onlyMember: true });

    render(<PeopleScreen />);

    expect(screen.getByLabelText('workspace.revoke.actionFor')).toBeTruthy();
  });

  it('offers no way to end access to a viewer who is not the owner', () => {
    showing({ people: [MARIA_SEEN_BY_ANOTHER, IONUT_VIEWING], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.queryByLabelText(/workspace.deactivate.actionFor/)).toBeNull();
  });

  it('offers the owner a way to end the access of somebody who has a manager', () => {
    showing({ people: [MARIA, IONUT], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.getByLabelText(/workspace.deactivate.actionFor/)).toBeDefined();
  });

  it('offers no way to end the access of the owner, who is the root', () => {
    showing({ people: [MARIA, IONUT], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.getAllByLabelText(/workspace.deactivate.actionFor/)).toHaveLength(1);
  });

  it('offers no way to erase anybody to a viewer who is not the owner', () => {
    showing({ people: [MARIA_SEEN_BY_ANOTHER, IONUT_VIEWING, ANDREI], onlyMember: false });

    render(<PeopleScreen />);
    fireEvent.click(screen.getByLabelText('workspace.people.showDeactivated'));

    expect(screen.queryByLabelText(/workspace.erase.actionFor/)).toBeNull();
  });

  it('offers erase only for somebody who has already left', () => {
    showing({ people: [MARIA, IONUT, ANDREI], onlyMember: false });

    render(<PeopleScreen />);
    fireEvent.click(screen.getByLabelText('workspace.people.showDeactivated'));

    expect(screen.getAllByLabelText(/workspace.erase.actionFor/)).toHaveLength(1);
    expect(screen.queryByLabelText(/workspace.erase.actionFor/)?.textContent).toBe(
      'workspace.erase.action',
    );
  });

  it('offers no Withdraw control to a viewer who may not see invitations', () => {
    showing({ people: [MARIA, IONUT], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.queryByLabelText('workspace.revoke.actionFor')).toBeNull();
  });

  it('announces the withdrawal in the live region once the server has agreed', () => {
    showing({ people: [MARIA], invitations: [PENDING], onlyMember: true });

    render(<PeopleScreen />);
    fireEvent.click(screen.getByLabelText('workspace.revoke.actionFor'));
    fireEvent.click(screen.getByText('confirm-withdrawal'));

    expect(within(screen.getByRole('status')).getByText('workspace.revoke.withdrawn')).toBeTruthy();
  });

  it('offers Move on a person who has a manager, and never on the owner', () => {
    showing({ people: [MARIA, IONUT], invitations: [], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.getByLabelText('workspace.move.actionFor')).toBeTruthy();
    expect(screen.getAllByLabelText('workspace.move.actionFor')).toHaveLength(1);
  });

  it('offers no Move control to a manager, on the very row it offers the owner', () => {
    showing({ people: [MARIA_SEEN_BY_ANOTHER, IONUT_VIEWING], invitations: [], onlyMember: false });

    render(<PeopleScreen />);

    expect(screen.queryByLabelText('workspace.move.actionFor')).toBeNull();
  });

  it('announces the move in the live region once the server has agreed', () => {
    showing({ people: [MARIA, IONUT], invitations: [], onlyMember: false });

    render(<PeopleScreen />);
    fireEvent.click(screen.getByLabelText('workspace.move.actionFor'));
    fireEvent.click(screen.getByText('confirm-move'));

    expect(within(screen.getByRole('status')).getByText('workspace.move.moved')).toBeTruthy();
  });

  it('shows no count, score or ranking of anybody', () => {
    showing({ people: [MARIA, IONUT, ANDREI], invitations: [], onlyMember: false });

    const { container } = render(<PeopleScreen />);

    expect(container.textContent).not.toMatch(/\b\d+\s*(tasks?|points?|%|of\s+\d+)/i);
  });
});
