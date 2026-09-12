// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { Candidate, Template } from '../api/processApi';

import { StartRunDialog } from './StartRunDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const startMutate = vi.fn();
let startState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useProcesses', () => ({
  useStartInstance: () => ({ ...startState, mutate: startMutate, reset: vi.fn() }),
}));

const TEMPLATE: Template = {
  id: 'template-1',
  name: 'Integrare angajat nou',
  overview: null,
  active: true,
  authorId: 'maria',
  createdAt: '2026-08-12T09:00:00Z',
  steps: [
    {
      id: 'a',
      taskTemplateId: 'tt-1',
      title: 'Pregătește echipamentul',
      description: null,
      expectedDurationHours: 4,
      position: 0,
    },
  ],
  dependencies: [],
};

const STEERERS: Candidate[] = [
  { id: 'ioana', displayName: 'Ioana Radu' },
  { id: 'elena', displayName: 'Elena Dobre' },
];

function draw(template: Template | null = TEMPLATE, steerers: Candidate[] = STEERERS) {
  const onClose = vi.fn();
  const onStarted = vi.fn();
  render(
    <StartRunDialog
      template={template}
      steerers={steerers}
      onClose={onClose}
      onStarted={onStarted}
    />,
  );
  return { onClose, onStarted };
}

beforeEach(() => {
  startState = { isPending: false, error: undefined };
  startMutate.mockReset();
});

afterEach(cleanup);

describe('the start-a-run dialog', () => {
  it('offers every active member as somebody who could steer the run', () => {
    draw();

    expect(screen.getByRole('option', { name: 'Ioana Radu' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Elena Dobre' })).toBeTruthy();
  });

  it('sends the template, the name and who steers it', () => {
    draw();
    fireEvent.change(screen.getByLabelText('process.start.name'), {
      target: { value: 'Integrare — Andrei' },
    });
    fireEvent.change(screen.getByLabelText('process.start.owner'), {
      target: { value: 'ioana' },
    });

    fireEvent.click(screen.getByText('process.start.confirm'));

    expect(startMutate).toHaveBeenCalledTimes(1);
    const [sent] = startMutate.mock.calls[0] as [
      { templateId: string; name: string; processOwnerId: string },
    ];
    expect(sent).toEqual({
      templateId: 'template-1',
      name: 'Integrare — Andrei',
      processOwnerId: 'ioana',
    });
  });

  it('hands back the run it started, so somebody can be shown it', () => {
    const { onStarted } = draw();
    startMutate.mockImplementation(
      (_input: unknown, handlers: { onSuccess: (instance: { id: string }) => void }) => {
        handlers.onSuccess({ id: 'run-7' });
      },
    );
    fireEvent.change(screen.getByLabelText('process.start.name'), {
      target: { value: 'Integrare — Andrei' },
    });
    fireEvent.change(screen.getByLabelText('process.start.owner'), {
      target: { value: 'ioana' },
    });

    fireEvent.click(screen.getByText('process.start.confirm'));

    expect(onStarted).toHaveBeenCalledWith({ id: 'run-7' });
  });

  it('will not submit without both a name and somebody to steer it', () => {
    draw();
    fireEvent.change(screen.getByLabelText('process.start.name'), {
      target: { value: 'Integrare — Andrei' },
    });

    fireEvent.click(screen.getByText('process.start.confirm'));

    expect(startMutate).not.toHaveBeenCalled();
  });

  it('tells somebody the process was changed rather than that something went wrong', () => {
    startState = {
      isPending: false,
      error: new ApiError(409, { code: 'GRAPH_CYCLE', message: 'waiting for each other' }),
    };

    draw();

    expect(screen.queryByText('process.start.error.GRAPH_CYCLE')).not.toBeNull();
    expect(screen.queryByText('process.start.error.UNKNOWN')).toBeNull();
  });

  it('names the deactivated-owner refusal on its own terms', () => {
    startState = {
      isPending: false,
      error: new ApiError(422, { code: 'PROCESS_OWNER_NOT_ACTIVE', message: 'no access' }),
    };

    draw();

    expect(screen.queryByText('process.start.error.PROCESS_OWNER_NOT_ACTIVE')).not.toBeNull();
  });

  it('guards against a second submission while the first is in flight', () => {
    startState = { isPending: true, error: undefined };

    draw();

    expect(screen.queryByText('process.start.submitting')).not.toBeNull();
  });
});
