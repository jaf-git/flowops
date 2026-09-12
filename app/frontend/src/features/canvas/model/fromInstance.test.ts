import { describe, expect, it } from 'vitest';

import {
  lifecycleStateOfStep,
  nodesFromInstance,
  type InstancePayload,
  type InstanceStepPayload,
} from './fromInstance';

function step(over: Partial<InstanceStepPayload> = {}): InstanceStepPayload {
  return {
    id: 's1',
    title: 'Pregătirea contractului',
    position: 0,
    condition: 'ASSIGNED',
    taskId: 't1',
    taskState: 'IN_PROGRESS',
    blockedReason: null,
    assigneeId: 'p1',
    assigneeName: 'Ana Neagu',
    deadline: '2026-08-20T09:00:00Z',
    atRisk: false,
    phases: [{ kind: 'ACTIVE', seconds: 3600 }],
    ...over,
  };
}

function instance(over: Partial<InstancePayload> = {}): InstancePayload {
  return {
    id: 'i1',
    name: 'Integrare — Elena Dobre',
    state: 'RUNNING',
    progress: { closed: 1, total: 3 },
    steps: [step()],
    edges: [],
    awaitingAssignment: [],
    ...over,
  };
}

describe('turning the read model into a plane', () => {
  it('numbers a step from its position, because a person counts from one', () => {
    const { steps } = nodesFromInstance(instance({ steps: [step({ position: 3 })] }), translate);

    expect(steps[0]?.stepNumber).toBe(4);
  });

  it('carries the server’s at-risk answer rather than working one out', () => {
    const { steps } = nodesFromInstance(instance({ steps: [step({ atRisk: true })] }), translate);

    expect(steps[0]?.atRisk).toBe(true);
  });

  it('carries the server’s satisfied flag on every edge', () => {
    const { edges } = nodesFromInstance(
      instance({
        edges: [
          { from: 'a', to: 'b', satisfied: true },
          { from: 'b', to: 'c', satisfied: false },
        ],
      }),
      translate,
    );

    expect(edges).toEqual([
      { from: 'a', to: 'b', satisfied: true },
      { from: 'b', to: 'c', satisfied: false },
    ]);
  });

  it('reads an erased assignee as somebody with no name, not as nobody', () => {
    const { steps } = nodesFromInstance(
      instance({ steps: [step({ assigneeId: 'p1', assigneeName: '' })] }),
      translate,
    );

    expect(steps[0]?.assignee).toEqual({ name: null });
  });

  it('leaves an unassigned step with nobody on it', () => {
    const { steps } = nodesFromInstance(
      instance({ steps: [step({ assigneeId: null, assigneeName: null })] }),
      translate,
    );

    expect(steps[0]?.assignee).toBeUndefined();
  });

  it('offers the plane’s one call to action exactly where the server says work is waiting', () => {
    const { steps } = nodesFromInstance(
      instance({
        steps: [step({ id: 'a', condition: 'REACHABLE' }), step({ id: 'b' })],
        awaitingAssignment: ['a'],
      }),
      translate,
    );

    expect(steps[0]?.offersAssignment).toBe(true);
    expect(steps[1]?.offersAssignment).toBe(false);
  });

  it('drops a phase kind the breakdown cannot render rather than folding it into another', () => {
    const { steps } = nodesFromInstance(
      instance({
        steps: [
          step({
            phases: [
              { kind: 'ACTIVE', seconds: 60 },
              { kind: 'SOMETHING_NEW', seconds: 900 },
            ],
          }),
        ],
      }),
      translate,
    );

    expect(steps[0]?.phases).toEqual([{ phase: 'active', seconds: 60 }]);
  });

  it('leaves a step with no recorded time without a phase footer at all', () => {
    const { steps } = nodesFromInstance(instance({ steps: [step({ phases: [] })] }), translate);

    expect(steps[0]?.phases).toBeUndefined();
  });

  it('maps every condition and every task state the product can produce', () => {
    const conditions: Array<InstanceStepPayload['condition']> = [
      'PENDING',
      'REACHABLE',
      'ASSIGNED',
      'CLOSED',
    ];
    const states = [
      'CREATED',
      'ACCEPTED',
      'IN_PROGRESS',
      'BLOCKED',
      'COMPLETED',
      'APPROVED',
      'CLOSED',
    ];

    for (const condition of conditions) {
      const { steps } = nodesFromInstance(instance({ steps: [step({ condition })] }), translate);
      expect(steps[0]?.condition, `${condition} maps to nothing`).toBeDefined();
    }

    for (const taskState of states) {
      const { steps } = nodesFromInstance(instance({ steps: [step({ taskState })] }), translate);
      expect(steps[0]?.taskState, `${taskState} maps to nothing`).toBeDefined();
    }
  });

  it('shows no state at all for one it does not recognise, rather than guessing', () => {
    const { steps } = nodesFromInstance(
      instance({ steps: [step({ taskState: 'SOMETHING_NEW' })] }),
      translate,
    );

    expect(steps[0]?.taskState).toBeUndefined();
  });
});

describe('the state handed to the action rail', () => {
  it('narrows to the seven the rail knows, and refuses anything else', () => {
    expect(lifecycleStateOfStep(step({ taskState: 'IN_PROGRESS' }))).toBe('IN_PROGRESS');
    expect(lifecycleStateOfStep(step({ taskState: 'SOMETHING_NEW' }))).toBeUndefined();
    expect(lifecycleStateOfStep(step({ taskState: 'in_progress' }))).toBeUndefined();
    expect(lifecycleStateOfStep(step({ taskState: null }))).toBeUndefined();
  });

  it('accepts every state the product can actually produce', () => {
    for (const state of [
      'CREATED',
      'ACCEPTED',
      'IN_PROGRESS',
      'BLOCKED',
      'COMPLETED',
      'APPROVED',
      'CLOSED',
    ]) {
      expect(lifecycleStateOfStep(step({ taskState: state })), `${state} was refused`).toBe(state);
    }
  });
});

describe('a pending step naming what holds it', () => {
  const holdsUp = (): InstancePayload =>
    instance({
      steps: [
        step({ id: 'a', position: 2, condition: 'CLOSED' }),
        step({ id: 'b', position: 3, condition: 'CLOSED' }),
        step({ id: 'c', position: 5, condition: 'PENDING' }),
      ],
      edges: [
        { from: 'a', to: 'c', satisfied: false },
        { from: 'b', to: 'c', satisfied: false },
      ],
    });

  it('names every unsatisfied dependency by the number a person sees', () => {
    const { steps } = nodesFromInstance(holdsUp(), translate);

    expect(steps[2]?.meta).toEqual([{ label: 'Waits on', value: 'Steps 3, 4' }]);
  });

  it('says step rather than steps when only one holds it', () => {
    const one = instance({
      steps: [
        step({ id: 'a', position: 5, condition: 'CLOSED' }),
        step({ id: 'c', condition: 'PENDING' }),
      ],
      edges: [{ from: 'a', to: 'c', satisfied: false }],
    });

    const { steps } = nodesFromInstance(one, translate);

    expect(steps[1]?.meta).toEqual([{ label: 'Waits on', value: 'Step 6' }]);
  });

  it('says nothing about dependencies that are already met', () => {
    const met = instance({
      steps: [
        step({ id: 'a', position: 0, condition: 'CLOSED' }),
        step({ id: 'c', condition: 'PENDING' }),
      ],
      edges: [{ from: 'a', to: 'c', satisfied: true }],
    });

    const { steps } = nodesFromInstance(met, translate);

    expect(steps[1]?.meta).toBeUndefined();
  });

  it('leaves a step that is not pending alone', () => {
    const reachable = instance({
      steps: [
        step({ id: 'a', position: 0, condition: 'CLOSED' }),
        step({ id: 'c', condition: 'REACHABLE' }),
      ],
      edges: [{ from: 'a', to: 'c', satisfied: false }],
    });

    const { steps } = nodesFromInstance(reachable, translate);

    expect(steps[1]?.meta).toBeUndefined();
  });
});

function translate(key: string, values?: Record<string, string | number>): string {
  const dictionary: Record<string, string> = {
    'canvas.node.waitsOn': 'Waits on',
    'canvas.node.waitsOnStep': `Step ${values?.steps}`,
    'canvas.node.waitsOnSteps': `Steps ${values?.steps}`,
  };
  return dictionary[key] ?? key;
}
