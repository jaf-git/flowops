// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { Person } from '../api/workspaceApi';
import { DeactivatePersonDialog } from './DeactivatePersonDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key}:${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const deactivate = {
  mutate: vi.fn(),
  isPending: false,
  error: null as Error | null,
};

vi.mock('../hooks/usePeople', () => ({
  useDeactivatePerson: () => deactivate,
}));

beforeEach(() => {
  deactivate.mutate = vi.fn();
  deactivate.isPending = false;
  deactivate.error = null;
});

afterEach(cleanup);

function person(overrides: Partial<Person> & Pick<Person, 'membershipId' | 'displayName'>): Person {
  return {
    personId: `person-${overrides.membershipId}`,
    role: 'EMPLOYEE',
    managerId: null,
    status: 'ACTIVE',
    deactivatedAt: null,
    isSelf: false,
    ...overrides,
  };
}

const MARIA = person({ membershipId: 'maria', displayName: 'Maria Ionescu', role: 'OWNER' });
const IONUT = person({
  membershipId: 'ionut',
  displayName: 'Ionuț Petrescu',
  role: 'MANAGER',
  managerId: 'maria',
});
const IOANA = person({ membershipId: 'ioana', displayName: 'Ioana Radu', managerId: 'ionut' });
const ANDREI = person({
  membershipId: 'andrei',
  displayName: 'Andrei Munteanu',
  managerId: 'ioana',
});

const EVERYBODY = [MARIA, IONUT, IOANA, ANDREI];

describe('the deactivate person dialog', () => {
  it('is not rendered until somebody is chosen', () => {
    render(
      <DeactivatePersonDialog
        person={undefined}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('names the reports that will move and the manager they will move to', () => {
    render(
      <DeactivatePersonDialog
        person={IONUT}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    const preview = screen.getByRole('dialog').textContent ?? '';
    expect(preview).toContain('Ioana Radu');
    expect(preview).toContain('Maria Ionescu');
    expect(preview).toContain('workspace.deactivate.reportsMove');
  });

  it('does not promise to move somebody who reports to one of the reports', () => {
    render(
      <DeactivatePersonDialog
        person={IONUT}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    expect(screen.getByRole('dialog').textContent).not.toContain('Andrei Munteanu');
  });

  it('says plainly when nobody else is affected', () => {
    render(
      <DeactivatePersonDialog
        person={ANDREI}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    const preview = screen.getByRole('dialog').textContent ?? '';
    expect(preview).toContain('workspace.deactivate.noReports');
    expect(preview).not.toContain('workspace.deactivate.reportsMove');
  });

  it('sends the membership and reports the person by name once it is done', () => {
    const onDeactivated = vi.fn();
    const onClose = vi.fn();
    deactivate.mutate = vi.fn((_id: string, options?: { onSuccess?: () => void }) =>
      options?.onSuccess?.(),
    );

    render(
      <DeactivatePersonDialog
        person={IONUT}
        people={EVERYBODY}
        onClose={onClose}
        onDeactivated={onDeactivated}
      />,
    );
    fireEvent.click(screen.getByText('workspace.deactivate.confirm'));

    expect(deactivate.mutate).toHaveBeenCalledWith('ionut', expect.anything());
    expect(onDeactivated).toHaveBeenCalledWith('Ionuț Petrescu');
    expect(onClose).toHaveBeenCalled();
  });

  it('disables the confirm control while the request is in flight', () => {
    deactivate.isPending = true;

    render(
      <DeactivatePersonDialog
        person={IONUT}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    expect(
      screen.getByText('workspace.deactivate.working').getAttribute('disabled'),
    ).not.toBeNull();
  });

  it('replaces the preview when the last owner cannot be deactivated', () => {
    deactivate.error = new ApiError(422, { code: 'ONLY_OWNER', message: 'refused' });

    render(
      <DeactivatePersonDialog
        person={MARIA}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    expect(screen.getByText('workspace.deactivate.onlyOwner')).toBeDefined();
    expect(screen.queryByText('workspace.deactivate.confirm')).toBeNull();
    expect(screen.getByRole('dialog').textContent).not.toContain('workspace.deactivate.accessEnds');
  });

  it('says nothing changed when the request fails for another reason', () => {
    deactivate.error = new ApiError(500, { code: 'UNKNOWN', message: 'boom' });

    render(
      <DeactivatePersonDialog
        person={IONUT}
        people={EVERYBODY}
        onClose={vi.fn()}
        onDeactivated={vi.fn()}
      />,
    );

    expect(screen.getByText('workspace.deactivate.failed')).toBeDefined();
  });
});
