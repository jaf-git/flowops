// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RemoveTaskFromProcessDialog } from './RemoveTaskFromProcessDialog';
import type { Instance, InstanceStep } from '../api/processApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const remove = { isPending: false, error: undefined, mutate: vi.fn(), reset: vi.fn() };

vi.mock('../hooks/useProcesses', () => ({
  useRemoveTaskFromInstance: () => remove,
}));

function step(overrides: Partial<InstanceStep> = {}): InstanceStep {
  return {
    id: 'step-1',
    definitionId: 'def-1',
    title: 'Pregătește echipamentul',
    description: null,
    expectedDurationHours: 4,
    position: 0,
    condition: 'ASSIGNED',
    taskId: 'task-7',
    planned: false,
    optional: false,
    conditionNote: null,
    skipped: false,
    dependsOn: [],
    taskState: 'IN_PROGRESS',
    blockedReason: null,
    assigneeId: null,
    assigneeName: null,
    deadline: null,
    atRisk: false,
    ...overrides,
  };
}

function instance(steps: InstanceStep[]): Instance {
  return {
    id: 'run-1',
    name: 'Integrare — Elena',
    state: 'RUNNING',
    templateId: 'template-1',
    templateName: 'Integrare angajat nou',
    processOwnerId: 'maria',
    startedAt: '2026-08-16T08:00:00Z',
    completedAt: null,
    progress: { closed: 0, total: steps.length },
    steps,
    awaitingAssignment: [],
    bottleneck: null,
    totalDurationMinutes: null,

    abandonedAt: null,
    abandonedReason: null,
    needingAttention: [],
  };
}

describe('RemoveTaskFromProcessDialog', () => {
  afterEach(cleanup);

  beforeEach(() => {
    remove.mutate.mockClear();
  });

  it('says the task itself survives', () => {
    const first = step();
    render(
      <RemoveTaskFromProcessDialog
        open
        instance={instance([first, step({ id: 'step-2' })])}
        step={first}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('process.removeTask.taskSurvives')).not.toBeNull();
  });

  it('says something different for a step nobody has started', () => {
    const planned = step({
      taskId: null,
      planned: true,
      optional: false,
      conditionNote: null,
      skipped: false,
      condition: 'PENDING',
    });
    render(
      <RemoveTaskFromProcessDialog
        open
        instance={instance([planned, step({ id: 'step-2' })])}
        step={planned}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('process.removeTask.plannedStepGoes')).not.toBeNull();
    expect(screen.queryByText('process.removeTask.taskSurvives')).toBeNull();
  });

  it('names that dependents will open, before the act', () => {
    const first = step();
    const waiting = step({ id: 'step-2', title: 'Prima zi', dependsOn: ['step-1'] });

    render(
      <RemoveTaskFromProcessDialog
        open
        instance={instance([first, waiting])}
        step={first}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('process.removeTask.opens')).not.toBeNull();
    expect(screen.queryByText('process.removeTask.nothingWaits')).toBeNull();
  });

  it('says plainly when nothing was waiting for it', () => {
    const first = step();
    render(
      <RemoveTaskFromProcessDialog
        open
        instance={instance([first, step({ id: 'step-2' })])}
        step={first}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('process.removeTask.nothingWaits')).not.toBeNull();
  });

  it('removes the step it was opened on', () => {
    const first = step();
    render(
      <RemoveTaskFromProcessDialog
        open
        instance={instance([first, step({ id: 'step-2' })])}
        step={first}
        onClose={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByText('process.removeTask.submit'));

    expect(remove.mutate).toHaveBeenCalledWith('step-1', expect.anything());
  });

  it('renders nothing when no step is chosen', () => {
    const { container } = render(
      <RemoveTaskFromProcessDialog
        open
        instance={instance([step()])}
        step={null}
        onClose={vi.fn()}
      />,
    );

    expect(container.innerHTML).toBe('');
  });
});
