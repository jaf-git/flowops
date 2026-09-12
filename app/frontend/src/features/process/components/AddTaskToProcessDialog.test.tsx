// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AddTaskToProcessDialog } from './AddTaskToProcessDialog';
import type { AttachableTask, Instance, InstanceStep } from '../api/processApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined || typeof options === 'string' ? key : `${key}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let attachable: AttachableTask[] = [];
const add = { isPending: false, error: undefined, mutate: vi.fn(), reset: vi.fn() };

let attachableFailed = false;

vi.mock('../hooks/useProcesses', () => ({
  useAttachableTasks: () => ({
    isPending: false,
    isError: attachableFailed,
    data: attachableFailed ? undefined : { tasks: attachable },
  }),
  useAssignablePeople: () => ({
    isError: false,
    data: { people: [{ id: 'elena', displayName: 'Elena Marinescu' }] },
  }),
  useAddTaskToInstance: () => add,
}));

function step(overrides: Partial<InstanceStep> = {}): InstanceStep {
  return {
    id: 'step-1',
    definitionId: 'def-1',
    title: 'Pregătește echipamentul',
    description: null,
    expectedDurationHours: 4,
    position: 0,
    condition: 'REACHABLE',
    taskId: null,
    planned: true,
    optional: false,
    conditionNote: null,
    skipped: false,
    dependsOn: [],
    taskState: null,
    blockedReason: null,
    assigneeId: null,
    assigneeName: null,
    deadline: null,
    atRisk: false,
    ...overrides,
  };
}

function instance(): Instance {
  return {
    id: 'run-1',
    name: 'Integrare — Elena',
    state: 'RUNNING',
    templateId: 'template-1',
    templateName: 'Integrare angajat nou',
    processOwnerId: 'maria',
    startedAt: '2026-08-16T08:00:00Z',
    completedAt: null,
    progress: { closed: 0, total: 1 },
    steps: [step()],
    awaitingAssignment: ['step-1'],
    bottleneck: null,
    totalDurationMinutes: null,

    abandonedAt: null,
    abandonedReason: null,
    needingAttention: [],
  };
}

describe('AddTaskToProcessDialog', () => {
  afterEach(cleanup);

  beforeEach(() => {
    attachable = [
      {
        id: 'task-7',
        title: 'Reconciliază extrasele',
        state: 'IN_PROGRESS',
        assigneeId: 'elena',
        deadline: null,
      },
    ];
    add.mutate.mockClear();
  });

  it('says what attaching does not do', () => {
    render(
      <AddTaskToProcessDialog open instance={instance()} onClose={vi.fn()} onAdded={vi.fn()} />,
    );

    expect(screen.getByText('process.addTask.attachChangesNothing')).not.toBeNull();
  });

  it('sends the chosen task and the steps it runs after', () => {
    render(
      <AddTaskToProcessDialog open instance={instance()} onClose={vi.fn()} onAdded={vi.fn()} />,
    );

    fireEvent.change(screen.getByLabelText(/process.addTask.field.existing/), {
      target: { value: 'task-7' },
    });
    fireEvent.click(screen.getByLabelText('Pregătește echipamentul'));
    fireEvent.click(screen.getByText('process.addTask.submit'));

    expect(add.mutate).toHaveBeenCalledWith(
      { taskId: 'task-7', dependsOnStepIds: ['step-1'] },
      expect.anything(),
    );
  });

  it('says why the list is empty rather than rendering an empty picker', () => {
    attachable = [];

    render(
      <AddTaskToProcessDialog open instance={instance()} onClose={vi.fn()} onAdded={vi.fn()} />,
    );

    expect(screen.getByText('process.addTask.noneAttachable.heading')).not.toBeNull();
    expect(screen.queryByLabelText(/process.addTask.field.existing/)).toBeNull();
  });

  it('sends a new task when that mode is chosen', () => {
    render(
      <AddTaskToProcessDialog open instance={instance()} onClose={vi.fn()} onAdded={vi.fn()} />,
    );

    fireEvent.click(screen.getByText('process.addTask.mode.new'));
    fireEvent.change(screen.getByLabelText(/process.addTask.field.title/), {
      target: { value: 'Comandă badge-ul' },
    });
    fireEvent.change(screen.getByLabelText(/process.addTask.field.assignee/), {
      target: { value: 'elena' },
    });
    fireEvent.click(screen.getByText('process.addTask.submit'));

    expect(add.mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        newTask: expect.objectContaining({ title: 'Comandă badge-ul', assigneeId: 'elena' }),
      }),
      expect.anything(),
    );
  });

  it('cannot be submitted before the mode it is in has what it needs', () => {
    render(
      <AddTaskToProcessDialog open instance={instance()} onClose={vi.fn()} onAdded={vi.fn()} />,
    );

    expect(
      screen.getByText('process.addTask.submit').closest('button')?.hasAttribute('disabled'),
    ).toBe(true);

    fireEvent.click(screen.getByText('process.addTask.mode.new'));
    expect(
      screen.getByText('process.addTask.submit').closest('button')?.hasAttribute('disabled'),
    ).toBe(true);
  });

  it('says the list could not be loaded rather than claiming there is nothing to add', () => {
    attachableFailed = true;

    render(
      <AddTaskToProcessDialog open instance={instance()} onClose={vi.fn()} onAdded={vi.fn()} />,
    );

    expect(screen.getByText('process.addTask.attachableLoadFailed')).not.toBeNull();
    expect(screen.queryByText('process.addTask.noneAttachable.heading')).toBeNull();
  });
});
