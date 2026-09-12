// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { CompleteTaskDialog } from './CompleteTaskDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const completeMutate = vi.fn();
const completeReset = vi.fn();
let completeState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useTasks', () => ({
  useCompleteTask: () => ({ ...completeState, mutate: completeMutate, reset: completeReset }),
}));

beforeEach(() => {
  completeState = { isPending: false, error: undefined };
  completeMutate.mockReset();
  completeReset.mockReset();
});

afterEach(cleanup);

function open() {
  return render(
    <CompleteTaskDialog taskId="task-1" onClose={() => undefined} onCompleted={() => undefined} />,
  );
}

function submitButton(): HTMLButtonElement {
  return screen.getByText('task.complete.submit').closest('button') as HTMLButtonElement;
}

describe('CompleteTaskDialog', () => {
  it('will not submit without a note, because the review would have nothing to review', () => {
    open();

    expect(submitButton().disabled).toBe(true);
  });

  it('will not submit a note of spaces either', () => {
    open();
    fireEvent.change(screen.getByLabelText('task.complete.field.note'), {
      target: { value: '   ' },
    });

    expect(submitButton().disabled).toBe(true);
  });

  it('submits with a note and no link at all, because the link is optional', () => {
    open();
    fireEvent.change(screen.getByLabelText('task.complete.field.note'), {
      target: { value: 'Compared both quarters and sent the summary.' },
    });

    expect(submitButton().disabled).toBe(false);
    fireEvent.click(submitButton());

    expect(completeMutate.mock.calls[0]?.[0]).toEqual({
      id: 'task-1',
      note: 'Compared both quarters and sent the summary.',
      externalLink: '',
    });
  });

  it('carries the link through untouched when there is one', () => {
    open();
    fireEvent.change(screen.getByLabelText('task.complete.field.note'), {
      target: { value: 'Done.' },
    });
    fireEvent.change(screen.getByLabelText('task.complete.field.link'), {
      target: { value: 'https://drive.example.ro/q3-review' },
    });
    fireEvent.click(submitButton());

    expect(completeMutate.mock.calls[0]?.[0]).toEqual({
      id: 'task-1',
      note: 'Done.',
      externalLink: 'https://drive.example.ro/q3-review',
    });
  });

  it('disables submission while the request is in flight', () => {
    completeState = { isPending: true, error: undefined };
    open();
    fireEvent.change(screen.getByLabelText('task.complete.field.note'), {
      target: { value: 'Done.' },
    });

    const submitting = screen
      .getByText('task.complete.submitting')
      .closest('button') as HTMLButtonElement;
    expect(submitting.disabled).toBe(true);
    expect(screen.queryByText('task.complete.submit')).toBeNull();
  });

  it('says which rule refused', () => {
    completeState = {
      isPending: false,
      error: new ApiError(409, { code: 'ILLEGAL_TRANSITION', message: 'This task has moved on.' }),
    };
    open();

    expect(screen.getByText('task.complete.error.ILLEGAL_TRANSITION')).toBeTruthy();
  });

  it('gives the note a multi-line control', () => {
    open();

    expect(screen.getByLabelText('task.complete.field.note')).toBeInstanceOf(HTMLTextAreaElement);
  });
});
