import { describe, expect, it } from 'vitest';

import { REPORT_STATES, summarise, type ReportRunInput, type ReportStepInput } from './summary';

function stateOf(step: ReportStepInput): 'notStarted' | 'inProgress' | 'blocked' | 'done' {
  if (step.condition === 'CLOSED') {
    return 'done';
  }
  if (step.blockedReason !== null || step.taskState === 'BLOCKED') {
    return 'blocked';
  }
  if (step.condition === 'ASSIGNED') {
    return 'inProgress';
  }
  return 'notStarted';
}

function step(over: Partial<ReportStepInput> & { id: string }): ReportStepInput {
  return {
    title: `Step ${over.id}`,
    position: 1,
    condition: 'REACHABLE',
    taskId: null,
    taskState: null,
    blockedReason: null,
    expectedDurationHours: null,
    ...over,
  };
}

function run(over: Partial<ReportRunInput> & { id: string }): ReportRunInput {
  const steps = over.steps ?? [];
  return {
    name: `Run ${over.id}`,
    state: 'RUNNING',
    progress: { closed: 0, total: steps.length },
    awaitingAssignment: [],
    bottleneck: null,
    ...over,
    steps,
  };
}

describe('the report over the runs a person may see', () => {
  it('counts the runs that are live apart from the runs that are visible', () => {
    const report = summarise(
      [run({ id: 'a' }), run({ id: 'b' }), run({ id: 'c', state: 'COMPLETE' })],
      stateOf,
    );

    expect(report.activeRuns).toBe(2);
    expect(report.runs).toBe(3);
  });

  it('adds the progress the server reported rather than recounting the steps', () => {
    const report = summarise(
      [
        run({ id: 'a', progress: { closed: 3, total: 4 } }),
        run({ id: 'b', progress: { closed: 1, total: 4 } }),
      ],
      stateOf,
    );

    expect(report.closed).toBe(4);
    expect(report.total).toBe(8);
    expect(report.completionPercent).toBe(50);
  });

  it('says nothing is done rather than dividing by nothing', () => {
    const report = summarise([], stateOf);

    expect(report.completionPercent).toBe(0);
    expect(report.total).toBe(0);
  });

  it('counts what needs attention once, however many ways it qualifies', () => {
    const report = summarise(
      [
        run({
          id: 'a',
          steps: [step({ id: 's1', blockedReason: 'Waiting on the supplier' }), step({ id: 's2' })],
          awaitingAssignment: ['s1', 's2'],
        }),
      ],
      stateOf,
    );

    expect(report.blocked).toBe(1);
    expect(report.awaitingAssignment).toBe(2);
    expect(report.needsAttention).toBe(2);
  });

  it('estimates the work still open, and says how much of it carries no estimate', () => {
    const report = summarise(
      [
        run({
          id: 'a',
          steps: [
            step({ id: 's1', condition: 'CLOSED', expectedDurationHours: 8 }),
            step({ id: 's2', condition: 'ASSIGNED', expectedDurationHours: 3 }),
            step({ id: 's3', expectedDurationHours: 1.5 }),
            step({ id: 's4' }),
          ],
        }),
      ],
      stateOf,
    );

    expect(report.openHours).toBe(4.5);
    expect(report.unestimatedOpen).toBe(1);
  });

  it('distributes every step across the four states the contract can prove', () => {
    const report = summarise(
      [
        run({
          id: 'a',
          steps: [
            step({ id: 's1', condition: 'CLOSED' }),
            step({ id: 's2', condition: 'ASSIGNED' }),
            step({ id: 's3', taskState: 'BLOCKED' }),
            step({ id: 's4' }),
          ],
        }),
      ],
      stateOf,
    );

    expect(report.distribution.map((row) => row.state)).toEqual(REPORT_STATES);
    expect(report.distribution.map((row) => row.count)).toEqual([1, 1, 1, 1]);
    expect(report.distribution.every((row) => row.share === 0.25)).toBe(true);
  });

  it('leaves every state on the chart at nothing rather than dropping the empty ones', () => {
    const report = summarise([], stateOf);

    expect(report.distribution).toHaveLength(REPORT_STATES.length);
    expect(report.distribution.every((row) => row.count === 0 && row.share === 0)).toBe(true);
  });

  describe('where the work is waiting', () => {
    it('names the step and how long it has waited, longest first', () => {
      const report = summarise(
        [
          run({
            id: 'a',
            name: 'Comandă mobilier',
            steps: [step({ id: 's1', title: 'Sună furnizorul' })],
            bottleneck: { stepId: 's1', waitedMinutes: 120 },
          }),
          run({
            id: 'b',
            name: 'Livrare atelier',
            steps: [step({ id: 's9', title: 'Pregătește transportul' })],
            bottleneck: { stepId: 's9', waitedMinutes: 480 },
          }),
        ],
        stateOf,
      );

      expect(report.waiting.map((row) => row.stepTitle)).toEqual([
        'Pregătește transportul',
        'Sună furnizorul',
      ]);
      expect(report.waiting.map((row) => row.minutes)).toEqual([480, 120]);
      expect(report.waiting.map((row) => row.share)).toEqual([1, 0.25]);
      expect(report.waiting.map((row) => row.runName)).toEqual([
        'Livrare atelier',
        'Comandă mobilier',
      ]);
    });

    it('says nothing about a run that has finished, because nothing is waiting in it', () => {
      const report = summarise(
        [
          run({
            id: 'a',
            state: 'COMPLETE',
            steps: [step({ id: 's1', condition: 'CLOSED' })],
            bottleneck: { stepId: 's1', waitedMinutes: 900 },
          }),
        ],
        stateOf,
      );

      expect(report.waiting).toEqual([]);
    });

    it('says nothing about a step that has since closed', () => {
      const report = summarise(
        [
          run({
            id: 'a',
            steps: [step({ id: 's1', condition: 'CLOSED' })],
            bottleneck: { stepId: 's1', waitedMinutes: 900 },
          }),
        ],
        stateOf,
      );

      expect(report.waiting).toEqual([]);
    });

    it('drops a bottleneck naming a step this response does not carry', () => {
      const report = summarise(
        [
          run({
            id: 'a',
            steps: [step({ id: 's1' })],
            bottleneck: { stepId: 'a-step-from-somewhere-else', waitedMinutes: 900 },
          }),
        ],
        stateOf,
      );

      expect(report.waiting).toEqual([]);
    });
  });

  it('emits no identifier and no figure that is about a person', () => {
    const report = summarise(
      [
        {
          ...run({
            id: 'a',
            steps: [step({ id: 's1', taskId: 'task-of-andrei', condition: 'CLOSED' })],
            progress: { closed: 1, total: 1 },
          }),
          processOwnerId: 'person-andrei',
        } as ReportRunInput,
      ],
      stateOf,
    );

    const serialised = JSON.stringify(report);

    expect(serialised).not.toContain('andrei');
    expect(serialised).not.toContain('Owner');
    expect(serialised).not.toContain('assignee');
  });
});
