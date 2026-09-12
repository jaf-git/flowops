import { describe, expect, it } from 'vitest';

import type { Band, BandCard } from './bands';
import { MOST_ROWS, planeOf, runNodeId, runsPerColumn } from './plane';

function card(
  id: string,
  dependsOn: string[] = [],
  state: BandCard['state'] = 'notStarted',
): BandCard {
  return {
    id,
    title: id,
    taskId: null,
    state,
    blockedReason: null,
    taskState: null,
    bottleneckMinutes: null,
    description: null,
    assigneeId: null,
    assigneeName: null,
    deadline: null,
    atRisk: false,
    dependsOn,
  };
}

function band(id: string, cards: BandCard[]): Band {
  return {
    id,
    name: id,
    running: true,
    paused: false,
    ownerId: 'o',
    progress: { closed: 0, total: cards.length },
    cards,
  };
}

const section = (bands: Band[]) => [group('s', 'Section', false, bands)];

function group(key: string, title: string, named: boolean, bands: Band[]) {
  return { key, title, named, lanes: [{ key: `${key}-lane`, titled: false, bands }] };
}

const dependencies = (plane: ReturnType<typeof planeOf>) =>
  plane.links.filter((link) => link.kind === 'DEPENDENCY');

const at = (plane: ReturnType<typeof planeOf>, id: string) =>
  plane.cards.find((each) => each.card.id === id);

describe('how deep a group stacks its runs', () => {
  it('stacks more of them as there are more to stack', () => {
    const few = runsPerColumn(6, 900, 200);
    const many = runsPerColumn(60, 900, 200);

    expect(many).toBeGreaterThan(few);
  });

  it('stacks a wide run deeper than a narrow one, to keep the block the shape of a window', () => {
    expect(runsPerColumn(24, 1600, 200)).toBeGreaterThan(runsPerColumn(24, 400, 200));
  });

  it('never asks for more columns than there are runs', () => {
    expect(runsPerColumn(1, 900, 200)).toBe(1);
    expect(runsPerColumn(3, 5000, 100)).toBeLessThanOrEqual(3);
  });

  it('answers rather than dividing by nothing', () => {
    expect(runsPerColumn(5, 0, 0)).toBe(5);
  });
});

describe('where a card sits', () => {
  it('puts steps that wait for the same thing in the same column', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b', ['a']), card('c', ['a'])])]));

    expect(at(plane, 'b')?.x).toBe(at(plane, 'c')?.x);
    expect(at(plane, 'b')?.x).toBeGreaterThan(at(plane, 'a')?.x ?? 0);
    expect(at(plane, 'b')?.y).not.toBe(at(plane, 'c')?.y);
  });

  it('moves a step right for each thing it waits behind', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b', ['a']), card('c', ['b'])])]));

    const xs = ['a', 'b', 'c'].map((id) => at(plane, id)?.x ?? 0);

    expect(xs[0]).toBeLessThan(xs[1] as number);
    expect(xs[1]).toBeLessThan(xs[2] as number);
  });

  it('ignores a dependency on a step that is not in this run', () => {
    const plane = planeOf(section([band('r', [card('a', ['elsewhere'])])]));

    expect(dependencies(plane)).toHaveLength(0);
  });

  it('does not hang on a cycle that should not exist', () => {
    const plane = planeOf(section([band('r', [card('a', ['b']), card('b', ['a'])])]));

    expect(plane.cards).toHaveLength(2);
  });
});

describe('a depth that is too tall wraps', () => {
  const many = Array.from({ length: 7 }, (_, index) => card(`s${String(index)}`));

  it('never stacks more than the readable few in one column', () => {
    const plane = planeOf(section([band('r', many)]));

    const perColumn = new Map<number, number>();
    for (const placed of plane.cards) {
      perColumn.set(placed.x, (perColumn.get(placed.x) ?? 0) + 1);
    }

    expect(perColumn.size).toBeGreaterThan(1);
    for (const height of perColumn.values()) {
      expect(height).toBeLessThanOrEqual(MOST_ROWS);
    }
  });

  it('keeps reading order across the wrap', () => {
    const plane = planeOf(section([band('r', many)]));

    expect(at(plane, 's1')?.y).toBe(at(plane, 's0')?.y);
    expect(at(plane, 's1')?.x).toBeGreaterThan(at(plane, 's0')?.x ?? 0);
  });

  it('leaves a short run in one column', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b')])]));

    expect(new Set(plane.cards.map((placed) => placed.x)).size).toBe(1);
  });
});

describe('what is linked', () => {
  it('draws one link per dependency, pointing at the step that waits', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b', ['a'])])]));

    expect(dependencies(plane)).toEqual([
      { id: 'a->b', from: 'a', to: 'b', met: false, kind: 'DEPENDENCY' },
    ]);
  });

  it('marks a link met only when the step it waits for is finished', () => {
    const plane = planeOf(section([band('r', [card('a', [], 'done'), card('b', ['a'])])]));

    expect(dependencies(plane)[0]?.met).toBe(true);
  });

  it('never links across two runs', () => {
    const plane = planeOf(section([band('r1', [card('a')]), band('r2', [card('b', ['a'])])]));

    expect(dependencies(plane)).toHaveLength(0);
  });

  it('ties every run to where its steps begin', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b', ['a'])])]));

    const membership = plane.links.filter((link) => link.kind === 'MEMBERSHIP');

    expect(membership).toEqual([
      { id: `${runNodeId('r')}->a`, from: runNodeId('r'), to: 'a', met: false, kind: 'MEMBERSHIP' },
    ]);
  });
});

describe('a run says what it is', () => {
  it('gives every run a head, to the left of its steps', () => {
    const plane = planeOf(section([band('r1', [card('a')]), band('r2', [card('b')])]));

    expect(plane.runs.map((run) => run.band.id)).toEqual(['r1', 'r2']);
    expect(plane.runs[0]?.x).toBeLessThan(at(plane, 'a')?.x ?? 0);
  });

  it('lines a head up with the first row of its own steps', () => {
    const plane = planeOf(section([band('r', [card('a')])]));

    expect(plane.runs[0]?.y).toBe(at(plane, 'a')?.y);
  });
});

describe('one plane, groups beside each other', () => {
  it('orders the runs of one column downwards', () => {
    const bands = Array.from({ length: 9 }, (_, index) =>
      band(`r${String(index)}`, [card(`c${String(index)}`)]),
    );
    const plane = planeOf(section(bands));

    const columns = new Map<number, number[]>();
    for (const run of plane.runs) {
      columns.set(run.box.x, [...(columns.get(run.box.x) ?? []), run.box.y]);
    }

    const stacked = [...columns.values()].filter((ys) => ys.length > 1);
    expect(stacked.length).toBeGreaterThan(0);
    for (const ys of stacked) {
      expect(ys).toEqual([...ys].sort((first, second) => first - second));
      expect(new Set(ys).size).toBe(ys.length);
    }
  });

  it('starts another column of runs rather than stacking a group forever', () => {
    const bands = Array.from({ length: 12 }, (_, index) =>
      band(`r${String(index)}`, [card(`c${String(index)}`)]),
    );
    const plane = planeOf(section(bands));

    const columns = new Map<number, number[]>();
    for (const run of plane.runs) {
      columns.set(run.x, [...(columns.get(run.x) ?? []), run.y]);
    }

    expect(columns.size).toBeGreaterThan(1);

    const top = plane.runs[0]?.y;
    for (const ys of columns.values()) {
      expect(Math.min(...ys)).toBe(top);
    }
  });

  it('leaves a single run as a single column', () => {
    const plane = planeOf(section([band('r1', [card('a'), card('b', ['a'])])]));

    expect(plane.runs).toHaveLength(1);
    expect(new Set(plane.runs.map((run) => run.box.x)).size).toBe(1);
  });

  it('keeps a wrapped group inside its own panel', () => {
    const bands = Array.from({ length: 12 }, (_, index) =>
      band(`r${String(index)}`, [card(`c${String(index)}`)]),
    );
    const plane = planeOf([
      group('one', 'One', true, bands),
      group('two', 'Two', false, [band('other', [card('z')])]),
    ]);

    const [one, two] = plane.sections;
    for (const run of plane.runs.filter((each) => each.band.id !== 'other')) {
      expect(run.x).toBeGreaterThanOrEqual(one?.x ?? 0);
      expect(run.x).toBeLessThan((one?.x ?? 0) + (one?.width ?? 0));
    }
    expect((one?.x ?? 0) + (one?.width ?? 0)).toBeLessThanOrEqual(two?.x ?? 0);
  });

  it('puts each group to the right of the last, starting at the same height', () => {
    const plane = planeOf([
      group('one', 'One', true, [band('r1', [card('a')])]),
      group('two', 'Two', false, [band('r2', [card('b')])]),
    ]);

    expect(plane.sections.map((each) => each.key)).toEqual(['one', 'two']);
    expect(plane.sections[0]?.x).toBeLessThan(plane.sections[1]?.x ?? 0);
    expect(plane.sections[0]?.y).toBe(plane.sections[1]?.y);
    expect(at(plane, 'a')?.x).toBeLessThan(at(plane, 'b')?.x ?? 0);
  });

  it('sizes a group to hold everything filed under it', () => {
    const plane = planeOf([
      group('one', 'One', true, [band('r1', [card('a'), card('b')])]),
      group('two', 'Two', false, [band('r2', [card('c')])]),
    ]);

    const [one, two] = plane.sections;
    expect(one).toBeDefined();
    expect(two).toBeDefined();

    expect((one?.x ?? 0) + (one?.width ?? 0)).toBeLessThanOrEqual(two?.x ?? 0);
    for (const placed of plane.cards.filter((each) => each.runId === 'r1')) {
      expect(placed.x).toBeGreaterThanOrEqual(one?.x ?? 0);
      expect(placed.x).toBeLessThan((one?.x ?? 0) + (one?.width ?? 0));
    }
  });

  it('drops a group with no runs', () => {
    const plane = planeOf([group('empty', 'Empty', true, [])]);

    expect(plane.sections).toHaveLength(0);
    expect(plane.runs).toHaveLength(0);
  });

  it('reports a size that covers everything it placed', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b', ['a'])])]));

    expect(Math.max(...plane.cards.map((each) => each.x))).toBeLessThan(plane.width);
    expect(Math.max(...plane.cards.map((each) => each.y))).toBeLessThan(plane.height);
  });
});

describe('a run is a box', () => {
  it('gives every run a box that holds its own head and steps', () => {
    const plane = planeOf(section([band('r', [card('a'), card('b', ['a'])])]));
    const [only] = plane.runs;

    expect(only?.box.width).toBeGreaterThan(0);
    expect(only?.x).toBeGreaterThanOrEqual(only?.box.x ?? 0);
    for (const placed of plane.cards) {
      expect(placed.x).toBeGreaterThan(only?.box.x ?? 0);
      expect(placed.x).toBeLessThan((only?.box.x ?? 0) + (only?.box.width ?? 0));
      expect(placed.y).toBeGreaterThanOrEqual(only?.box.y ?? 0);
      expect(placed.y).toBeLessThan((only?.box.y ?? 0) + (only?.box.height ?? 0));
    }
  });

  it('never lets any two run boxes overlap, however they are packed', () => {
    const bands = Array.from({ length: 11 }, (_, index) =>
      band(`r${String(index)}`, [card(`c${String(index)}`), card(`d${String(index)}`)]),
    );
    const plane = planeOf(section(bands));

    expect(plane.runs.length).toBe(11);
    for (const first of plane.runs) {
      for (const second of plane.runs) {
        if (first === second) {
          continue;
        }
        const apart =
          first.box.x + first.box.width <= second.box.x ||
          second.box.x + second.box.width <= first.box.x ||
          first.box.y + first.box.height <= second.box.y ||
          second.box.y + second.box.height <= first.box.y;
        expect(apart, `${first.band.id} overlaps ${second.band.id}`).toBe(true);
      }
    }
  });
});

describe('lanes inside a group', () => {
  const twoPeople = [
    {
      key: 'SOLO',
      title: "One person's own",
      named: false,
      lanes: [
        { key: 'SOLO:ana', personId: 'ana', titled: true, bands: [band('r1', [card('a')])] },
        { key: 'SOLO:mihai', personId: 'mihai', titled: true, bands: [band('r2', [card('b')])] },
      ],
    },
  ];

  it('puts each lane to the right of the last, inside the one group', () => {
    const plane = planeOf(twoPeople);
    const [ana, mihai] = plane.lanes;

    expect(ana?.x).toBeLessThan(mihai?.x ?? 0);
    expect(ana?.y).toBe(mihai?.y);
    expect(at(plane, 'a')?.x).toBeLessThan(at(plane, 'b')?.x ?? 0);
  });

  it('holds both lanes inside the group panel that owns them', () => {
    const plane = planeOf(twoPeople);
    const [panel] = plane.sections;

    expect(plane.sections).toHaveLength(1);
    for (const lane of plane.lanes) {
      expect(lane.x).toBeGreaterThanOrEqual(panel?.x ?? 0);
      expect(lane.x + lane.width).toBeLessThanOrEqual((panel?.x ?? 0) + (panel?.width ?? 0));
    }
  });

  it('leaves room above a titled lane and none above a nameless one', () => {
    const titled = planeOf(twoPeople);
    const nameless = planeOf(section([band('r1', [card('a')])]));

    expect(titled.runs[0]?.y).toBeGreaterThan(nameless.runs[0]?.y ?? 0);
  });
});

describe('a group that is shut', () => {
  const two = [
    group('one', 'One', true, [band('r1', [card('a')])]),
    group('two', 'Two', false, [band('r2', [card('b')])]),
  ];

  it('draws nothing of a collapsed group but its bar', () => {
    const plane = planeOf(two, new Set(['one']));

    expect(plane.sections[0]?.collapsed).toBe(true);
    expect(plane.runs.map((run) => run.band.id)).toEqual(['r2']);
    expect(at(plane, 'a')).toBeUndefined();
  });

  it('still says how many runs are in it, which is a fact about the group', () => {
    const plane = planeOf(two, new Set(['one']));

    expect(plane.sections[0]?.runCount).toBe(1);
  });

  it('leaves the groups beside it where they were, only nearer', () => {
    const open = planeOf(two);
    const shut = planeOf(two, new Set(['one']));

    expect(shut.sections[1]?.x).toBeLessThan(open.sections[1]?.x ?? 0);
    expect(shut.sections[1]?.collapsed).toBe(false);
  });
});
