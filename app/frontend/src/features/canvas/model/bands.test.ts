import { describe, expect, it } from 'vitest';

import {
  bandsFrom,
  cardState,
  cardTone,
  healthOf,
  readyForSomebody,
  stepPosition,
  unsequencedIn,
  type BandCard,
  type BandInstanceInput,
  type BandStepInput,
} from './bands';

function step(over: Partial<BandStepInput> = {}): BandStepInput {
  return {
    id: 's1',
    title: 'Pregătește specificația',
    position: 0,
    condition: 'PENDING',
    taskId: null,
    taskState: null,
    blockedReason: null,

    description: null,
    assigneeId: null,
    assigneeName: null,
    deadline: null,
    atRisk: false,
    dependsOn: [],
    ...over,
  };
}

function instance(over: Partial<BandInstanceInput> = {}): BandInstanceInput {
  return {
    id: 'i1',
    name: 'Lansare produs',
    state: 'RUNNING',
    processOwnerId: 'p1',
    progress: { closed: 1, total: 4 },
    bottleneck: null,
    steps: [step()],
    ...over,
  };
}

describe('a card state, from what the server said', () => {
  it('is done for a closed step, whatever else the step carries', () => {
    expect(
      cardState(
        step({ condition: 'CLOSED', blockedReason: 'aștept avizul', taskState: 'BLOCKED' }),
      ),
    ).toBe('done');
  });

  it('is blocked whenever a reason exists, because blocked is the fact an owner must not miss', () => {
    expect(cardState(step({ condition: 'ASSIGNED', blockedReason: 'aștept avizul' }))).toBe(
      'blocked',
    );
    expect(cardState(step({ condition: 'ASSIGNED', taskState: 'BLOCKED' }))).toBe('blocked');
  });

  it('is in progress for an assigned step that is not blocked', () => {
    expect(cardState(step({ condition: 'ASSIGNED', taskState: 'IN_PROGRESS' }))).toBe('inProgress');
  });

  it('is not started for work that has been handed out but not begun', () => {
    expect(cardState(step({ condition: 'ASSIGNED', taskState: 'CREATED' }))).toBe('notStarted');
    expect(cardState(step({ condition: 'ASSIGNED', taskState: 'ACCEPTED' }))).toBe('notStarted');
  });

  it('stays in progress once work has begun, whatever the task went on to do', () => {
    for (const taskState of ['IN_PROGRESS', 'COMPLETED', 'APPROVED', 'CLOSED', null]) {
      expect(cardState(step({ condition: 'ASSIGNED', taskState }))).toBe('inProgress');
    }
  });

  it('is not started for pending and reachable alike — neither has anybody on it yet', () => {
    expect(cardState(step({ condition: 'PENDING' }))).toBe('notStarted');
    expect(cardState(step({ condition: 'REACHABLE' }))).toBe('notStarted');
  });
});

describe('the bands', () => {
  it('order cards by template position, whatever order the server sent them in', () => {
    const [band] = bandsFrom([
      instance({
        steps: [
          step({ id: 'third', position: 2 }),
          step({ id: 'first', position: 0 }),
          step({ id: 'second', position: 1 }),
        ],
      }),
    ]);

    expect(band?.cards.map((card) => card.id)).toEqual(['first', 'second', 'third']);
  });

  it('marks exactly the bottleneck step with its wait, and no other', () => {
    const [band] = bandsFrom([
      instance({
        bottleneck: { stepId: 'slow', waitedMinutes: 300 },
        steps: [step({ id: 'fine', position: 0 }), step({ id: 'slow', position: 1 })],
      }),
    ]);

    expect(band?.cards.find((card) => card.id === 'slow')?.bottleneckMinutes).toBe(300);
    expect(band?.cards.find((card) => card.id === 'fine')?.bottleneckMinutes).toBeNull();
  });

  it('carries per-process progress and nothing keyed to a person', () => {
    const [band] = bandsFrom([instance()]);

    expect(band?.progress).toEqual({ closed: 1, total: 4 });
    expect(Object.keys(band ?? {})).toEqual([
      'id',
      'name',
      'running',

      'paused',
      'ownerId',
      'progress',
      'cards',
    ]);
  });
});

describe('how a run is going', () => {
  const at = (over: Partial<BandCard>): BandCard => ({
    id: 'c',
    title: 'c',
    taskId: null,
    state: 'notStarted',
    blockedReason: null,
    taskState: null,
    bottleneckMinutes: null,
    description: null,
    assigneeId: null,
    assigneeName: null,
    deadline: null,
    atRisk: false,
    dependsOn: [],
    ...over,
  });

  it('calls a run blocked when any step is, whatever else is true', () => {
    expect(
      healthOf([at({ id: '1', state: 'done' }), at({ id: '2', state: 'blocked', atRisk: true })]),
    ).toBe('blocked');
  });

  it('calls a run at risk when a step still to do is running out of time', () => {
    expect(healthOf([at({ id: '1', state: 'inProgress', atRisk: true })])).toBe('atRisk');
  });

  it('does not call a run at risk over a step that is already done', () => {
    expect(healthOf([at({ id: '1', state: 'done', atRisk: true })])).toBe('onTrack');
  });

  it('names the step being worked rather than the last one finished', () => {
    expect(stepPosition({ progress: { closed: 2, total: 6 } })).toEqual({ at: 3, of: 6 });
  });

  it('has no step to be on once the run is finished', () => {
    expect(stepPosition({ progress: { closed: 6, total: 6 } })).toBeNull();
    expect(stepPosition({ progress: { closed: 0, total: 0 } })).toBeNull();
  });

  it('names the next step nobody holds, so the banner says what needs somebody', () => {
    const found = readyForSomebody([
      at({ id: '1', state: 'done' }),
      at({ id: '2', title: 'Send the quote', state: 'notStarted', assigneeId: null }),
    ]);

    expect(found?.title).toBe('Send the quote');
  });

  it('finds nothing to hand out when every unfinished step already has somebody', () => {
    expect(readyForSomebody([at({ id: '1', state: 'inProgress', assigneeId: 'ana' })])).toBeNull();
  });

  it('draws a step running out of time as attention without changing its state', () => {
    expect(cardTone({ state: 'inProgress', atRisk: true })).toBe('attention');
    expect(cardTone({ state: 'blocked', atRisk: true })).toBe('blocked');
    expect(cardTone({ state: 'done', atRisk: true })).toBe('done');
    expect(cardTone({ state: 'notStarted', atRisk: false })).toBe('notStarted');
  });
});

describe('which steps nobody has sequenced', () => {
  const at = (id: string, dependsOn: string[] = []): BandCard => ({
    id,
    title: id,
    taskId: null,
    state: 'notStarted',
    blockedReason: null,
    taskState: null,
    bottleneckMinutes: null,
    description: null,
    assigneeId: null,
    assigneeName: null,
    deadline: null,
    atRisk: false,
    dependsOn,
  });

  it('does not call an entry step unsequenced when something waits for it', () => {
    expect(unsequencedIn([at('a'), at('b', ['a'])])).toEqual([]);
  });

  it('names a step that depends on nothing and has nothing depending on it', () => {
    expect(unsequencedIn([at('a'), at('b', ['a']), at('loose')]).map((card) => card.id)).toEqual([
      'loose',
    ]);
  });

  it('names every step of a run where nothing was sequenced at all', () => {
    expect(unsequencedIn([at('a'), at('b'), at('c')])).toHaveLength(3);
  });

  it('never calls a run of one step unsequenced', () => {
    expect(unsequencedIn([at('only')])).toEqual([]);
    expect(unsequencedIn([])).toEqual([]);
  });
});
