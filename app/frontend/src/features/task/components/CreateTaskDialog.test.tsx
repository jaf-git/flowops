// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { CreateTaskDialog } from './CreateTaskDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const createMutate = vi.fn();
const createReset = vi.fn();
let createState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

const peopleState = {
  data: { people: [{ id: 'andrei', displayName: 'Andrei Munteanu' }] },
};

vi.mock('../../tasklib/hooks/useTaskTemplates', () => ({
  useTemplateSuggestions: () => ({ data: [], isPending: false, isError: false }),
  useStampTask: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock('../hooks/useTasks', () => ({
  useCreateTask: () => ({ ...createState, mutate: createMutate, reset: createReset }),
  useAssignablePeople: () => peopleState,
}));

beforeEach(() => {
  createState = { isPending: false, error: undefined };
  createMutate.mockReset();
  createReset.mockReset();
});

afterEach(cleanup);

describe('CreateTaskDialog', () => {
  it('offers a person whose access still stands', () => {
    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    expect(screen.getByRole('option', { name: 'Andrei Munteanu' })).toBeTruthy();
  });

  it('offers nobody the endpoint left out, rather than filtering the directory a second time', () => {
    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    const picker = within(screen.getByLabelText(/task.create.field.assignee/));
    expect(picker.queryByRole('option', { name: 'Elena Dobre' })).toBeNull();
    expect(picker.getAllByRole('option')).toHaveLength(2);
  });

  it('will not submit until there is a title, a person and a date', () => {
    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    fireEvent.click(screen.getByText('task.create.submit'));

    expect(createMutate).not.toHaveBeenCalled();
  });

  it('creates work with no date, and sends absent rather than empty', () => {
    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    fireEvent.change(screen.getByLabelText(/task.create.field.title/), {
      target: { value: 'Pregătește dosarul fiscal' },
    });
    fireEvent.change(screen.getByLabelText(/task.create.field.assignee/), {
      target: { value: 'andrei' },
    });

    expect(screen.getByRole('button', { name: 'task.create.submit' })).toHaveProperty(
      'disabled',
      false,
    );
    fireEvent.click(screen.getByText('task.create.submit'));

    expect(createMutate).toHaveBeenCalledTimes(1);
    expect((createMutate.mock.calls[0] as [{ deadline: string | null }])[0].deadline).toBeNull();
  });

  it.each([
    [
      'nobody to give it to',
      { title: 'Draft the supplier review', assignee: '', deadline: '2026-09-01T09:00' },
    ],
    ['nothing to do', { title: '   ', assignee: 'andrei', deadline: '2026-09-01T09:00' }],
  ])('will not submit with %s, whatever else is filled in', (_case, filled) => {
    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    fireEvent.change(screen.getByLabelText(/task.create.field.title/), {
      target: { value: filled.title },
    });
    fireEvent.change(screen.getByLabelText(/task.create.field.assignee/), {
      target: { value: filled.assignee },
    });
    fireEvent.change(screen.getByLabelText(/task.create.field.deadline/), {
      target: { value: filled.deadline },
    });

    expect(screen.getByRole('button', { name: 'task.create.submit' })).toHaveProperty(
      'disabled',
      true,
    );
    fireEvent.click(screen.getByText('task.create.submit'));
    expect(createMutate).not.toHaveBeenCalled();
  });

  it('sends the deadline as an instant rather than as the local string the control produced', () => {
    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    fireEvent.change(screen.getByLabelText(/task.create.field.title/), {
      target: { value: 'Draft the supplier review' },
    });
    fireEvent.change(screen.getByLabelText(/task.create.field.assignee/), {
      target: { value: 'andrei' },
    });
    fireEvent.change(screen.getByLabelText(/task.create.field.deadline/), {
      target: { value: '2026-09-01T09:00' },
    });
    fireEvent.click(screen.getByText('task.create.submit'));

    expect(createMutate).toHaveBeenCalled();
    const sent = createMutate.mock.calls[0]?.[0] as { deadline: string; assigneeId: string };
    expect(sent.assigneeId).toBe('andrei');
    expect(sent.deadline).toBe(new Date('2026-09-01T09:00').toISOString());
    expect(sent.deadline.endsWith('Z')).toBe(true);
  });

  it('tells somebody the date has passed when that is what was refused', () => {
    createState = {
      isPending: false,
      error: new ApiError(422, { code: 'DEADLINE_IN_THE_PAST', message: 'no' }),
    };

    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    expect(screen.getByText('task.create.error.DEADLINE_IN_THE_PAST')).toBeTruthy();
  });

  it('tells them the person is out of their scope when that is what was refused instead', () => {
    createState = {
      isPending: false,
      error: new ApiError(422, { code: 'ASSIGNEE_OUT_OF_SCOPE', message: 'no' }),
    };

    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    expect(screen.getByText('task.create.error.ASSIGNEE_OUT_OF_SCOPE')).toBeTruthy();
    expect(screen.queryByText('task.create.error.ASSIGNEE_NOT_ACTIVE')).toBeNull();
  });

  it('shows the in-flight label while the server is deciding, so nobody submits twice', () => {
    createState = { isPending: true, error: undefined };

    render(<CreateTaskDialog open onClose={() => undefined} onCreated={() => undefined} />);

    expect(screen.getByText('task.create.submitting')).toBeTruthy();
  });
});
