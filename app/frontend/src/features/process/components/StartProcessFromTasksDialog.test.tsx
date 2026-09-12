// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { StartProcessFromTasksDialog } from './StartProcessFromTasksDialog';
import { ApiError } from '../../../shared/api/client';
import type { AttachableTask } from '../api/processApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined || typeof options === 'string' ? key : `${key}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let offered: AttachableTask[] = [];
let offeredFailed = false;
const start: {
  isPending: boolean;
  error: Error | undefined;
  mutate: ReturnType<typeof vi.fn>;
  reset: ReturnType<typeof vi.fn>;
} = { isPending: false, error: undefined, mutate: vi.fn(), reset: vi.fn() };

vi.mock('../hooks/useProcesses', () => ({
  useTasksForANewProcess: () => ({
    isPending: false,
    isError: offeredFailed,
    data: offeredFailed ? undefined : { tasks: offered },
  }),
  useStartInstanceFromTasks: () => start,
}));

const STEERERS = [{ id: 'maria', displayName: 'Maria Ionescu' }];

function open(): void {
  render(
    <StartProcessFromTasksDialog open steerers={STEERERS} onClose={vi.fn()} onStarted={vi.fn()} />,
  );
}

function openWith(initialTaskIds: readonly string[]): void {
  render(
    <StartProcessFromTasksDialog
      open
      steerers={STEERERS}
      onClose={vi.fn()}
      onStarted={vi.fn()}
      initialTaskIds={initialTaskIds}
    />,
  );
}

function fillNameAndSteerer(): void {
  fireEvent.change(screen.getByLabelText(/process.startFromTasks.name/), {
    target: { value: 'Închidere lunară august' },
  });
  fireEvent.change(screen.getByLabelText(/process.startFromTasks.owner/), {
    target: { value: 'maria' },
  });
}

describe('StartProcessFromTasksDialog', () => {
  afterEach(cleanup);

  beforeEach(() => {
    offeredFailed = false;
    start.error = undefined;
    offered = [
      {
        id: 'task-7',
        title: 'Reconciliază extrasele',
        state: 'IN_PROGRESS',
        assigneeId: 'elena',
        deadline: null,
      },
      {
        id: 'task-9',
        title: 'Verifică facturile',
        state: 'ASSIGNED',
        assigneeId: 'elena',
        deadline: null,
      },
    ];
    start.mutate.mockClear();
  });

  it('asks for tasks and never for step titles or durations', () => {
    open();

    expect(screen.getByText('process.startFromTasks.tasks')).toBeTruthy();
    expect(screen.getByLabelText('Reconciliază extrasele')).toBeTruthy();
    expect(screen.queryByText(/expectedDuration/i)).toBeNull();
    expect(screen.queryByText(/process.author.step/)).toBeNull();
  });

  it('sends the chosen tasks in the order they were chosen', () => {
    open();
    fillNameAndSteerer();

    fireEvent.click(screen.getByLabelText('Verifică facturile'));
    fireEvent.click(screen.getByLabelText('Reconciliază extrasele'));
    fireEvent.click(screen.getByText('process.startFromTasks.confirm'));

    expect(start.mutate).toHaveBeenCalledWith(
      {
        name: 'Închidere lunară august',
        processOwnerId: 'maria',
        taskIds: ['task-9', 'task-7'],
      },
      expect.anything(),
    );
  });

  it('moves a chosen task up without unchoosing it', () => {
    open();
    fillNameAndSteerer();

    fireEvent.click(screen.getByLabelText('Reconciliază extrasele'));
    fireEvent.click(screen.getByLabelText('Verifică facturile'));

    const [, secondUp] = screen.getAllByLabelText('process.startFromTasks.up');
    expect(secondUp).toBeTruthy();
    fireEvent.click(secondUp as HTMLElement);
    fireEvent.click(screen.getByText('process.startFromTasks.confirm'));

    expect(start.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ taskIds: ['task-9', 'task-7'] }),
      expect.anything(),
    );
  });

  it('cannot be submitted with no tasks chosen', () => {
    open();
    fillNameAndSteerer();

    fireEvent.click(screen.getByText('process.startFromTasks.confirm'));

    expect(start.mutate).not.toHaveBeenCalled();
  });

  it('cannot be submitted without a name', () => {
    open();
    fireEvent.change(screen.getByLabelText(/process.startFromTasks.owner/), {
      target: { value: 'maria' },
    });
    fireEvent.click(screen.getByLabelText('Reconciliază extrasele'));

    fireEvent.click(screen.getByText('process.startFromTasks.confirm'));

    expect(start.mutate).not.toHaveBeenCalled();
  });

  it('says the list failed rather than claiming there is no work', () => {
    offeredFailed = true;

    open();

    expect(screen.getByText('process.startFromTasks.tasksLoadFailed')).toBeTruthy();
    expect(screen.queryByText('process.startFromTasks.noTasks.heading')).toBeNull();
  });

  it('names a refusal by its code rather than as something unknown', () => {
    start.error = new ApiError(409, { code: 'TASK_ALREADY_IN_A_PROCESS' });

    open();

    expect(screen.getByText('process.startFromTasks.error.TASK_ALREADY_IN_A_PROCESS')).toBeTruthy();
  });

  it('says something when the failure was not a refusal', () => {
    start.error = new TypeError('Failed to fetch');

    open();

    expect(screen.getByText('process.startFromTasks.error.UNKNOWN')).toBeTruthy();
  });

  it('warns when everything chosen is already finished', () => {
    offered = [
      {
        id: 'task-7',
        title: 'Reconciliază extrasele',
        state: 'CLOSED',
        assigneeId: 'elena',
        deadline: null,
      },
    ];

    open();
    fireEvent.click(screen.getByLabelText(/Reconciliază extrasele/));

    expect(screen.getByText('process.startFromTasks.allFinished')).toBeTruthy();
  });

  it('marks a finished task in the list', () => {
    offered = [
      {
        id: 'task-7',
        title: 'Reconciliază extrasele',
        state: 'CLOSED',
        assigneeId: 'elena',
        deadline: null,
      },
      {
        id: 'task-9',
        title: 'Verifică facturile',
        state: 'ASSIGNED',
        assigneeId: 'elena',
        deadline: null,
      },
    ];

    open();

    expect(
      screen.getByLabelText('Reconciliază extrasele · process.startFromTasks.alreadyFinished'),
    ).toBeTruthy();
    expect(screen.getByLabelText('Verifică facturile')).toBeTruthy();
  });

  it('says there is nothing to build a process from when the list is empty', () => {
    offered = [];

    open();

    expect(screen.getByText('process.startFromTasks.noTasks.heading')).toBeTruthy();
    expect(screen.queryByText('process.startFromTasks.tasksLoadFailed')).toBeNull();
  });

  it('starts from a selection made in the work queue, in the order it was made', () => {
    openWith(['task-9', 'task-7']);
    fillNameAndSteerer();

    fireEvent.click(screen.getByText('process.startFromTasks.confirm'));

    expect(start.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ taskIds: ['task-9', 'task-7'] }),
      expect.anything(),
    );
  });

  it('names a chosen task that has since joined a run, rather than dropping it', () => {
    openWith(['task-7', 'task-gone']);
    fillNameAndSteerer();

    expect(screen.getByText(/process.startFromTasks.missing.taken/)).toBeTruthy();

    fireEvent.click(screen.getByText('process.startFromTasks.confirm'));
    expect(start.mutate).not.toHaveBeenCalled();
  });

  it('says what is missing rather than disabling in silence', () => {
    open();

    expect(screen.getByText('process.startFromTasks.missing.name')).toBeTruthy();
    expect(screen.getByText('process.startFromTasks.missing.owner')).toBeTruthy();
    expect(screen.getByText('process.startFromTasks.missing.tasks')).toBeTruthy();

    fillNameAndSteerer();

    expect(screen.queryByText('process.startFromTasks.missing.name')).toBeNull();
    expect(screen.queryByText('process.startFromTasks.missing.owner')).toBeNull();
    expect(screen.getByText('process.startFromTasks.missing.tasks')).toBeTruthy();
  });

  it('will not name when the run lands while any chosen task has no date', () => {
    openWith(['task-7', 'task-9']);

    expect(screen.queryByText(/process.startFromTasks.lastLands/)).toBeNull();
    expect(screen.getByText(/process.startFromTasks.undated/)).toBeTruthy();
  });
});
