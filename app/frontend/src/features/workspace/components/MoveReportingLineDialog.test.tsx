// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { Person, ReassignPreview } from '../api/workspaceApi';
import { MoveReportingLineDialog } from './MoveReportingLineDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
let moveState: { isPending: boolean; isError: boolean; error: Error | null } = {
  isPending: false,
  isError: false,
  error: null,
};

let previewState: {
  isPending: boolean;
  isError: boolean;
  data: ReassignPreview | undefined;
  error: Error | null;
} = {
  isPending: false,
  isError: false,
  data: undefined,
  error: null,
};

vi.mock('../hooks/usePeople', () => ({
  useReassignReportingLine: () => ({ ...moveState, mutate, reset: vi.fn() }),
  useReassignPreview: () => previewState,
}));

function person(overrides: Partial<Person> & Pick<Person, 'membershipId' | 'displayName'>): Person {
  return {
    personId: `p-${overrides.membershipId}`,
    role: 'EMPLOYEE',
    managerId: 'maria',
    status: 'ACTIVE',
    deactivatedAt: null,
    isSelf: false,
    ...overrides,
  };
}

const MARIA = person({
  membershipId: 'maria',
  displayName: 'Maria Ionescu',
  role: 'OWNER',
  managerId: null,
});
const IONUT = person({ membershipId: 'ionut', displayName: 'Ionuț Petrescu', role: 'MANAGER' });
const IOANA = person({
  membershipId: 'ioana',
  displayName: 'Ioana Radu',
  role: 'MANAGER',
  managerId: 'ionut',
});
const ANDREI = person({
  membershipId: 'andrei',
  displayName: 'Andrei Munteanu',
  managerId: 'ioana',
});
const PEOPLE = [MARIA, IONUT, IOANA, ANDREI];

beforeEach(() => {
  mutate.mockReset();
  moveState = { isPending: false, isError: false, error: null };
  previewState = { isPending: false, isError: false, data: undefined, error: null };
});

afterEach(cleanup);

describe('moving somebody in the reporting line', () => {
  it('stays mounted and closed until a person is chosen', () => {
    const { container } = render(
      <MoveReportingLineDialog
        person={undefined}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );

    expect(container.querySelector('dialog')?.hasAttribute('open')).toBe(false);
  });

  it('offers only people who may hold reports, and never the person being moved', () => {
    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );

    const options = Array.from(screen.getAllByRole('option')).map((option) => option.textContent);
    expect(options).toContain('Maria Ionescu');
    expect(options).toContain('Ionuț Petrescu');
    expect(options).not.toContain('Andrei Munteanu');
    expect(options).not.toContain('Ioana Radu');
  });

  it('names everybody who would move, not just how many', () => {
    previewState = {
      isPending: false,
      isError: false,
      error: null,
      data: {
        personName: 'Ioana Radu',
        formerManagerName: 'Ionuț Petrescu',
        newManagerName: 'Maria Ionescu',
        movingWithThem: [{ membershipId: 'andrei', displayName: 'Andrei Munteanu' }],
        alreadyTheirManager: false,
      },
    };

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'maria' } });

    expect(screen.getByText('workspace.move.consequence.movesToo')).toBeTruthy();
    expect(screen.getByText('workspace.move.heading')).toBeTruthy();
  });

  it('refuses a manager from below on sight, without stating what the move would do', () => {
    previewState = {
      isPending: false,
      isError: false,
      error: null,
      data: {
        personName: 'Ionuț Petrescu',
        formerManagerName: 'Maria Ionescu',
        newManagerName: 'Ioana Radu',
        movingWithThem: [{ membershipId: 'ioana', displayName: 'Ioana Radu' }],
        alreadyTheirManager: false,
      },
    };

    render(
      <MoveReportingLineDialog
        person={IONUT}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ioana' } });

    expect(screen.getAllByText('workspace.move.cycle.step')).toHaveLength(1);
    expect(screen.getByText('workspace.move.cycle.conclusion')).toBeTruthy();

    expect(screen.queryByText('workspace.move.heading')).toBeNull();
    expect(screen.queryByText('workspace.move.consequence.movesToo')).toBeNull();
    expect(
      screen.getByRole('button', { name: 'workspace.move.confirm' }).hasAttribute('disabled'),
    ).toBe(true);
  });

  it('states no consequences once the server has refused the loop, either', () => {
    previewState = {
      isPending: false,
      isError: false,
      error: null,
      data: {
        personName: 'Ionuț Petrescu',
        formerManagerName: 'Maria Ionescu',
        newManagerName: 'Maria Ionescu',
        movingWithThem: [{ membershipId: 'ioana', displayName: 'Ioana Radu' }],
        alreadyTheirManager: false,
      },
    };
    moveState = {
      isPending: false,
      isError: true,
      error: new ApiError(422, {
        code: 'CYCLE',
        message: 'that move would make the reporting line loop back on itself',
        details: [
          { field: 'step', rule: 'ionut' },
          { field: 'step', rule: 'maria' },
        ],
      }),
    };

    render(
      <MoveReportingLineDialog
        person={IONUT}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'maria' } });

    expect(screen.getByText('workspace.move.cycle.conclusion')).toBeTruthy();
    expect(screen.queryByText('workspace.move.heading')).toBeNull();
    expect(screen.queryByText('workspace.move.consequence.movesToo')).toBeNull();
    expect(
      screen.getByRole('button', { name: 'workspace.move.confirm' }).hasAttribute('disabled'),
    ).toBe(true);
  });

  it('explains a cycle by naming the reporting path, one sentence per step', () => {
    moveState = {
      isPending: false,
      isError: true,
      error: new ApiError(422, {
        code: 'CYCLE',
        message: 'that move would make the reporting line loop back on itself',
        details: [
          { field: 'step', rule: 'ionut' },
          { field: 'step', rule: 'ioana' },
          { field: 'step', rule: 'andrei' },
        ],
      }),
    };

    render(
      <MoveReportingLineDialog
        person={IONUT}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );

    expect(screen.getAllByText('workspace.move.cycle.step')).toHaveLength(2);
    expect(screen.getByText('workspace.move.cycle.conclusion')).toBeTruthy();
  });

  it('names the specific rule for a refusal that is not a cycle', () => {
    moveState = {
      isPending: false,
      isError: true,
      error: new ApiError(422, { code: 'MANAGER_NOT_ELIGIBLE', message: 'ignored' }),
    };

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );

    expect(screen.getByText('workspace.move.error.MANAGER_NOT_ELIGIBLE')).toBeTruthy();
    expect(screen.queryByText('workspace.move.cycle.conclusion')).toBeNull();
  });

  it('says something when the failure is not one the server described', () => {
    moveState = {
      isPending: false,
      isError: true,
      error: new TypeError('Failed to fetch'),
    };

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );

    expect(screen.getByText('workspace.move.error.unexpected')).toBeTruthy();
  });

  it('refuses the confirmation when the preview could not be fetched', () => {
    previewState = {
      isPending: false,
      isError: true,
      data: undefined,
      error: new ApiError(401, { code: 'UNAUTHENTICATED', message: 'ignored' }),
    };

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'maria' } });

    expect(screen.getByText('workspace.move.previewFailed')).toBeTruthy();
    expect(screen.getByText('workspace.move.confirm').closest('button')?.disabled).toBe(true);
  });

  it('cannot be confirmed before a manager is chosen', () => {
    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );

    expect(screen.getByText('workspace.move.confirm').closest('button')?.disabled).toBe(true);
  });

  it('disables the confirmation while the move is in flight', () => {
    moveState = { isPending: true, isError: false, error: null };

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'maria' } });

    expect(screen.getByText('workspace.move.moving').closest('button')?.disabled).toBe(true);
    expect(screen.queryByText('workspace.move.confirm')).toBeNull();
  });

  it('refuses the confirmation while the preview is still in flight', () => {
    previewState = { isPending: true, isError: false, data: undefined, error: null };

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'maria' } });

    expect(screen.getByText('workspace.move.confirm').closest('button')?.disabled).toBe(true);
  });

  it('moves when confirmed and reports who moved where', () => {
    previewState = {
      isPending: false,
      isError: false,
      error: null,
      data: {
        personName: 'Ioana Radu',
        formerManagerName: 'Ionuț Petrescu',
        newManagerName: 'Maria Ionescu',
        movingWithThem: [],
        alreadyTheirManager: false,
      },
    };

    const onMoved = vi.fn();
    mutate.mockImplementation(
      (
        _input: { membershipId: string; proposedManagerId: string },
        options: { onSuccess: (result: { changed: boolean }) => void },
      ) => options.onSuccess({ changed: true }),
    );

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={onMoved}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'maria' } });
    fireEvent.click(screen.getByText('workspace.move.confirm'));

    expect(mutate).toHaveBeenCalledWith(
      { membershipId: 'ioana', proposedManagerId: 'maria' },
      expect.anything(),
    );
    expect(onMoved).toHaveBeenCalledWith('Ioana Radu', 'Maria Ionescu');
  });

  it('announces nothing when the proposal was already true', () => {
    const onMoved = vi.fn();
    mutate.mockImplementation(
      (
        _input: { membershipId: string; proposedManagerId: string },
        options: { onSuccess: (result: { changed: boolean }) => void },
      ) => options.onSuccess({ changed: false }),
    );

    render(
      <MoveReportingLineDialog
        person={IOANA}
        people={PEOPLE}
        onClose={vi.fn()}
        onMoved={onMoved}
      />,
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ionut' } });
    fireEvent.click(screen.getByText('workspace.move.confirm'));

    expect(onMoved).not.toHaveBeenCalled();
  });
});
