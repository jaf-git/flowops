// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { InstanceStep } from '../api/processApi';

import { AssignStepDialog } from './AssignStepDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const assignMutate = vi.fn();
const assignReset = vi.fn();
let assignState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

let peopleState: { isError: boolean; data?: { people: { id: string; displayName: string }[] } } = {
  isError: false,
  data: { people: [{ id: 'elena', displayName: 'Elena Dobre' }] },
};

let askedFor: string | null = null;

vi.mock('../hooks/useProcesses', () => ({
  useAssignStep: () => ({ ...assignState, mutate: assignMutate, reset: assignReset }),
  useAssignablePeople: (instanceId: string | null) => {
    askedFor = instanceId;
    return peopleState;
  },
}));

const STEP: InstanceStep = {
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
};

function draw(step: InstanceStep | null = STEP) {
  const onClose = vi.fn();
  const onAssigned = vi.fn();
  render(
    <AssignStepDialog instanceId="run-1" step={step} onClose={onClose} onAssigned={onAssigned} />,
  );
  return { onClose, onAssigned };
}

beforeEach(() => {
  assignState = { isPending: false, error: undefined };
  peopleState = { isError: false, data: { people: [{ id: 'elena', displayName: 'Elena Dobre' }] } };
  askedFor = null;
  assignMutate.mockReset();
  assignReset.mockReset();
});

afterEach(cleanup);

describe('the assignment dialog', () => {
  it('offers the people the endpoint returned', () => {
    draw();

    expect(screen.getByRole('option', { name: 'Elena Dobre' })).toBeTruthy();
  });

  it('asks for the list belonging to this run', () => {
    draw();

    expect(askedFor).toBe('run-1');
  });

  it('asks for nobody while the dialog is closed', () => {
    draw(null);

    expect(askedFor).toBeNull();
  });

  it('sends the step, the person and the deadline as an instant', () => {
    draw();
    fireEvent.change(screen.getByLabelText('process.assign.person'), {
      target: { value: 'elena' },
    });
    fireEvent.change(screen.getByLabelText('process.assign.deadline'), {
      target: { value: '2026-09-01T09:00' },
    });

    fireEvent.click(screen.getByText('process.assign.confirm'));

    expect(assignMutate).toHaveBeenCalledTimes(1);
    const [sent] = assignMutate.mock.calls[0] as [
      { stepId: string; assigneeId: string; deadline: string },
    ];
    expect(sent.stepId).toBe('step-1');
    expect(sent.assigneeId).toBe('elena');
    expect(sent.deadline).toBe(new Date('2026-09-01T09:00').toISOString());
  });

  it('suggests a date from the expected duration the template holds', () => {
    draw({ ...STEP, expectedDurationHours: 48 });

    const field = document.getElementById('assign-deadline') as HTMLInputElement;
    const expected = new Date(Date.now() + 48 * 3600 * 1000);
    expect(field.value.slice(0, 10)).toBe(
      new Date(expected.getTime() - expected.getTimezoneOffset() * 60_000)
        .toISOString()
        .slice(0, 10),
    );
  });

  it('suggests nothing when the template said nothing', () => {
    draw({ ...STEP, expectedDurationHours: null });

    expect((document.getElementById('assign-deadline') as HTMLInputElement).value).toBe('');
  });

  it('will not submit without a person', () => {
    draw();

    fireEvent.click(screen.getByText('process.assign.confirm'));

    expect(assignMutate).not.toHaveBeenCalled();
  });

  it('assigns a step with no date at all, which is the point of the change', () => {
    draw();
    fireEvent.change(screen.getByLabelText('process.assign.person'), {
      target: { value: 'elena' },
    });
    fireEvent.change(screen.getByLabelText('process.assign.deadline'), { target: { value: '' } });

    fireEvent.click(screen.getByText('process.assign.confirm'));

    expect(assignMutate).toHaveBeenCalledTimes(1);
    const sent = (assignMutate.mock.calls[0] as [{ deadline: string | null }])[0];
    expect(sent.deadline).toBeNull();
  });

  it('names the refusal it was given rather than reporting that something went wrong', () => {
    assignState = {
      isPending: false,
      error: new ApiError(409, { code: 'STEP_NOT_REACHABLE', message: 'still waiting' }),
    };

    draw();

    expect(screen.queryByText('process.assign.error.STEP_NOT_REACHABLE')).not.toBeNull();
    expect(screen.queryByText('process.assign.error.UNKNOWN')).toBeNull();
  });

  it('says when the list of people could not be loaded', () => {
    peopleState = { isError: true };

    draw();

    expect(screen.queryByText('process.assign.peopleFailed')).not.toBeNull();
  });

  it('guards against a second submission while the first is in flight', () => {
    assignState = { isPending: true, error: undefined };

    draw();

    expect(screen.queryByText('process.assign.submitting')).not.toBeNull();
  });
});
