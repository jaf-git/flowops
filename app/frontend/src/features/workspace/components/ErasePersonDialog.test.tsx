// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { ErasurePreview, Person } from '../api/workspaceApi';
import { ErasePersonDialog } from './ErasePersonDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key}:${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const preview = {
  data: undefined as ErasurePreview | undefined,
  isPending: false,
  isError: false,
};

const erase = {
  mutate: vi.fn(),
  reset: vi.fn(),
  isPending: false,
  error: null as Error | null,
};

const reauthenticate = {
  mutate: vi.fn(),
  reset: vi.fn(),
  isPending: false,
  error: null as Error | null,
};

vi.mock('../hooks/usePeople', () => ({
  useErasurePreview: () => preview,
  useErasePerson: () => erase,
}));

vi.mock('../hooks/useElevateSession', () => ({
  useElevateSession: () => reauthenticate,
}));

function previewOf(overrides: Partial<ErasurePreview> = {}): ErasurePreview {
  return {
    membershipId: 'ionut',
    displayName: 'Ionuț Petrescu',
    deactivatedAt: '2026-08-01T09:00:00Z',
    eligible: true,
    refusal: null,
    destroys: ['NAME', 'EMAIL_ADDRESS'],
    survives: ['AUTHORED_WORK', 'CONSENT_RECORD'],
    alreadyErased: false,
    ...overrides,
  };
}

beforeEach(() => {
  preview.data = previewOf();
  preview.isPending = false;
  preview.isError = false;
  erase.mutate = vi.fn();
  erase.reset = vi.fn();
  erase.isPending = false;
  erase.error = null;
  reauthenticate.mutate = vi.fn();
  reauthenticate.reset = vi.fn();
  reauthenticate.isPending = false;
  reauthenticate.error = null;
});

afterEach(cleanup);

const IONUT: Person = {
  membershipId: 'ionut',
  personId: 'person-ionut',
  displayName: 'Ionuț Petrescu',
  role: 'MANAGER',
  managerId: 'maria',
  status: 'DEACTIVATED',
  deactivatedAt: '2026-08-01T09:00:00Z',
  isSelf: false,
};

function fill(password: string, typedName: string): void {
  fireEvent.change(screen.getByLabelText(/workspace\.erase\.passwordLabel/), {
    target: { value: password },
  });
  fireEvent.change(screen.getByLabelText(/workspace\.erase\.typedNameLabel/), {
    target: { value: typedName },
  });
}

function confirmButton(): HTMLElement {
  return screen.getByRole('button', { name: /workspace\.erase\.confirm/ });
}

describe('the erase person dialog', () => {
  it('is not rendered until somebody is chosen', () => {
    render(<ErasePersonDialog person={undefined} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('says that words people typed are not rewritten, whatever the preview contains', () => {
    preview.data = previewOf({ destroys: [], survives: [] });

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByText('workspace.erase.freeTextIsNotRewritten')).not.toBeNull();
  });

  it('names what is destroyed and what survives, before anything happens', () => {
    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByText('workspace.erase.destroys.NAME')).not.toBeNull();
    expect(screen.queryByText('workspace.erase.destroys.EMAIL_ADDRESS')).not.toBeNull();
    expect(screen.queryByText('workspace.erase.survives.AUTHORED_WORK')).not.toBeNull();
    expect(screen.queryByText('workspace.erase.survives.CONSENT_RECORD')).not.toBeNull();
  });

  it('refuses to confirm with no password, and refuses with no typed name', () => {
    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    fill('', 'Ionuț Petrescu');
    expect(confirmButton()).toHaveProperty('disabled', true);

    fill('a password', '');
    expect(confirmButton()).toHaveProperty('disabled', true);

    fill('a password', 'Ionuț Petrescu');
    expect(confirmButton()).toHaveProperty('disabled', false);
  });

  it('refuses to confirm while the preview is still in flight', () => {
    preview.data = undefined;
    preview.isPending = true;

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByRole('button', { name: /workspace\.erase\.confirm/ })).not.toBeNull();
    expect(confirmButton()).toHaveProperty('disabled', true);
  });

  it('elevates the session first and then erases, rather than sending the password with the act', () => {
    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);
    fill('a password', 'Ionuț Petrescu');

    fireEvent.click(confirmButton());

    expect(reauthenticate.mutate).toHaveBeenCalledTimes(1);
    expect(reauthenticate.mutate.mock.calls[0]?.[0]).toBe('a password');
    expect(erase.mutate).not.toHaveBeenCalled();

    reauthenticate.mutate.mock.calls[0]?.[1]?.onSuccess?.();

    expect(erase.mutate).toHaveBeenCalledTimes(1);
    expect(erase.mutate.mock.calls[0]?.[0]).toEqual({
      membershipId: 'ionut',
      typedName: 'Ionuț Petrescu',
    });
  });

  it('keeps the fields when the typed name was wrong', () => {
    erase.error = new ApiError(422, { code: 'NAME_MISMATCH' });

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    fill('a password', 'Ioana Radu');
    fireEvent.click(confirmButton());

    expect(screen.queryByText('workspace.erase.nameMismatch')).not.toBeNull();
    expect(screen.queryByLabelText(/workspace\.erase\.typedNameLabel/)).not.toBeNull();
    expect(screen.queryByRole('button', { name: /workspace\.erase\.confirm/ })).not.toBeNull();
  });

  it('replaces the confirmation when the person is still working here', () => {
    preview.data = previewOf({ eligible: false, refusal: 'SUBJECT_ACTIVE' });

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByText(/workspace\.erase\.stillActive/)).not.toBeNull();
    expect(screen.queryByRole('button', { name: /workspace\.erase\.confirm/ })).toBeNull();
    expect(screen.queryByLabelText(/workspace\.erase\.typedNameLabel/)).toBeNull();
  });

  it('replaces the confirmation for the last owner', () => {
    preview.data = previewOf({ eligible: false, refusal: 'ONLY_OWNER' });

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByText('workspace.erase.onlyOwner')).not.toBeNull();
    expect(screen.queryByRole('button', { name: /workspace\.erase\.confirm/ })).toBeNull();
  });

  it('refuses the confirmation when the preview could not be fetched', () => {
    preview.data = undefined;
    preview.isError = true;

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    expect(screen.queryByText('workspace.erase.previewFailed')).not.toBeNull();
    expect(screen.queryByRole('button', { name: /workspace\.erase\.confirm/ })).toBeNull();
  });

  it('says something when the failure is not one the server described', () => {
    erase.error = new Error('the connection went away');

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);
    fill('a password', 'Ionuț Petrescu');
    fireEvent.click(confirmButton());

    expect(screen.queryByText('workspace.erase.failed')).not.toBeNull();
  });

  it('does not show one person refusal over the next person', () => {
    erase.error = new ApiError(422, { code: 'NAME_MISMATCH' });

    const { rerender } = render(
      <ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />,
    );
    fill('a password', 'Ioana Radu');
    fireEvent.click(confirmButton());
    expect(screen.queryByText('workspace.erase.nameMismatch')).not.toBeNull();

    rerender(
      <ErasePersonDialog
        person={{ ...IONUT, membershipId: 'andrei', displayName: 'Andrei Munteanu' }}
        onClose={vi.fn()}
        onErased={vi.fn()}
      />,
    );

    expect(screen.queryByText('workspace.erase.nameMismatch')).toBeNull();
  });

  it('forgets the typed name and the password when it opens on somebody else', () => {
    const { rerender } = render(
      <ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />,
    );
    fill('a password', 'Ionuț Petrescu');
    expect(confirmButton()).toHaveProperty('disabled', false);

    rerender(<ErasePersonDialog person={undefined} onClose={vi.fn()} onErased={vi.fn()} />);
    rerender(
      <ErasePersonDialog
        person={{ ...IONUT, membershipId: 'andrei', displayName: 'Andrei Munteanu' }}
        onClose={vi.fn()}
        onErased={vi.fn()}
      />,
    );

    expect(screen.getByLabelText(/workspace\.erase\.typedNameLabel/)).toHaveProperty('value', '');
    expect(screen.getByLabelText(/workspace\.erase\.passwordLabel/)).toHaveProperty('value', '');
    expect(confirmButton()).toHaveProperty('disabled', true);
  });

  it('shows the in-flight state while the erasure is running', () => {
    erase.isPending = true;

    render(<ErasePersonDialog person={IONUT} onClose={vi.fn()} onErased={vi.fn()} />);

    const busy = screen.getByRole('button', { name: 'workspace.erase.working' });
    expect(busy.getAttribute('aria-busy')).toBe('true');
    expect(busy).toHaveProperty('disabled', true);
    expect(screen.queryByRole('button', { name: /workspace\.erase\.confirm/ })).toBeNull();
  });
});
