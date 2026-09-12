import { describe, expect, it } from 'vitest';

import { depthFromEdges, heightOf, layoutGraph, NODE_WIDTH, type GraphEdge } from './layout';
import en from '../../../i18n/locales/en/common.json';
import { galleryEntries } from './gallery';
import type { CanvasNode } from './node';

const HOUR = 60 * 60;

function step(id: string, extra: Partial<CanvasNode> = {}): CanvasNode {
  return { id, title: id, condition: 'Pending', ...extra };
}

const STEPS = ['1', '2', '3', '4', '6', '7'].map((id) => step(id));

const EDGES: GraphEdge[] = [
  { from: '1', to: '3', satisfied: true },
  { from: '2', to: '4', satisfied: true },
  { from: '3', to: '6', satisfied: false },
  { from: '4', to: '6', satisfied: false },
  { from: '6', to: '7', satisfied: false },
];

function depthOf(placed: ReturnType<typeof layoutGraph>, id: string): number {
  const found = placed.find((node) => node.id === id);

  if (found === undefined) {
    throw new Error(`${id} was not placed`);
  }

  return found.depth;
}

function columnOf(placed: ReturnType<typeof layoutGraph>, id: string): number {
  const found = placed.find((node) => node.id === id);

  if (found === undefined) {
    throw new Error(`${id} was not placed`);
  }

  return found.x;
}

describe('laying a running process out', () => {
  it('places every step it is given, and nothing it is not', () => {
    const placed = layoutGraph(STEPS, EDGES);

    expect(placed.map((node) => node.id).sort()).toEqual(['1', '2', '3', '4', '6', '7']);
  });

  it('puts steps that can run at the same time in the same column', () => {
    const placed = layoutGraph(STEPS, EDGES);

    expect(depthOf(placed, '3')).toBe(depthOf(placed, '4'));
    expect(columnOf(placed, '3')).toBe(columnOf(placed, '4'));

    expect(depthOf(placed, '3')).not.toBe(depthOf(placed, '6'));
  });

  it('puts a step to the right of everything it waits on', () => {
    const placed = layoutGraph(STEPS, EDGES);

    expect(depthOf(placed, '6')).toBeGreaterThan(depthOf(placed, '3'));
    expect(depthOf(placed, '6')).toBeGreaterThan(depthOf(placed, '4'));
    expect(depthOf(placed, '7')).toBeGreaterThan(depthOf(placed, '6'));
  });

  it('starts steps that wait on nothing at the left edge', () => {
    const placed = layoutGraph(STEPS, EDGES);

    expect(depthOf(placed, '1')).toBe(0);
    expect(depthOf(placed, '2')).toBe(0);
    expect(columnOf(placed, '1')).toBe(0);
  });

  it('leaves a column at least the gap the visual contract asks for', () => {
    const placed = layoutGraph(STEPS, EDGES);

    expect(columnOf(placed, '3') - columnOf(placed, '1') - NODE_WIDTH).toBeGreaterThanOrEqual(76);

    const column = placed.filter((node) => node.depth === 0).sort((a, b) => a.y - b.y);
    const above = column[0];
    const below = column[1];

    expect(
      below === undefined ? 0 : below.y - ((above?.y ?? 0) + (above?.height ?? 0)),
    ).toBeGreaterThanOrEqual(40);
  });

  it('never overlaps two nodes in one column', () => {
    const placed = layoutGraph(STEPS, EDGES);
    const first = placed.filter((node) => node.depth === 0).sort((a, b) => a.y - b.y);

    expect(first.length).toBeGreaterThan(1);

    for (let index = 1; index < first.length; index += 1) {
      const above = first[index - 1];
      const below = first[index];

      expect(below?.y ?? 0).toBeGreaterThanOrEqual((above?.y ?? 0) + (above?.height ?? 0));
    }
  });

  it('draws the same picture whatever order the steps arrived in', () => {
    const shuffled = [...STEPS].reverse();
    const edgesShuffled = [...EDGES].reverse();

    expect(layoutGraph(shuffled, edgesShuffled)).toEqual(layoutGraph(STEPS, EDGES));
  });

  it('places a step whose dependency it was never given, rather than losing it', () => {
    const placed = layoutGraph(STEPS, [...EDGES, { from: 'unknown', to: '7', satisfied: false }]);

    expect(placed.map((node) => node.id)).toContain('7');
  });

  it('places a graph with no dependencies at all as one column', () => {
    const placed = layoutGraph(STEPS, []);

    expect(new Set(placed.map((node) => node.depth))).toEqual(new Set([0]));
  });

  const LOPSIDED = ['a', 'b', 'c', 'd', 'e'].map((id) => step(id));
  const LOPSIDED_EDGES: GraphEdge[] = [
    { from: 'a', to: 'b', satisfied: false },
    { from: 'b', to: 'c', satisfied: false },
    { from: 'c', to: 'e', satisfied: false },
    { from: 'a', to: 'd', satisfied: false },
    { from: 'd', to: 'e', satisfied: false },
  ];

  it('puts every step in the column the graph says, on a lopsided graph', () => {
    const placed = layoutGraph(LOPSIDED, LOPSIDED_EDGES);
    const columns = Object.fromEntries(placed.map((node) => [node.id, node.depth]));

    expect(columns).toEqual({ a: 0, b: 1, c: 2, d: 1, e: 3 });
  });

  it('puts every step in the column the graph says, on the converging fixture', () => {
    const placed = layoutGraph(STEPS, EDGES);
    const columns = Object.fromEntries(placed.map((node) => [node.id, node.depth]));

    expect(columns).toEqual({ '1': 0, '2': 0, '3': 1, '4': 1, '6': 2, '7': 3 });
  });
});

describe('dependency depth, computed from the graph alone', () => {
  it('is zero for a step that waits on nothing', () => {
    expect(depthFromEdges(STEPS, EDGES).get('1')).toBe(0);
  });

  it('counts the longest chain, not the shortest', () => {
    const steps = ['a', 'b', 'c', 'd'].map((id) => step(id));
    const edges: GraphEdge[] = [
      { from: 'a', to: 'b', satisfied: false },
      { from: 'b', to: 'c', satisfied: false },
      { from: 'a', to: 'c', satisfied: false },
      { from: 'c', to: 'd', satisfied: false },
    ];

    expect(depthFromEdges(steps, edges).get('c')).toBe(2);
    expect(depthFromEdges(steps, edges).get('d')).toBe(3);
  });

  it('ignores an edge naming a step it was not given', () => {
    const steps = [step('a')];

    expect(depthFromEdges(steps, [{ from: 'ghost', to: 'a', satisfied: false }]).get('a')).toBe(0);
  });

  it('answers the same way for a cycle whatever order the steps arrive in', () => {
    const steps = ['a', 'b'].map((id) => step(id));
    const edges: GraphEdge[] = [
      { from: 'a', to: 'b', satisfied: false },
      { from: 'b', to: 'a', satisfied: false },
    ];

    expect([...depthFromEdges(steps, edges)]).toEqual([
      ...depthFromEdges([...steps].reverse(), edges),
    ]);
  });

  it('terminates on a cycle rather than hanging the browser', () => {
    const steps = ['a', 'b'].map((id) => step(id));
    const edges: GraphEdge[] = [
      { from: 'a', to: 'b', satisfied: false },
      { from: 'b', to: 'a', satisfied: false },
    ];

    expect(() => depthFromEdges(steps, edges)).not.toThrow();
  });
});

describe('how tall a node will be', () => {
  it('gives a step that is not yet work the smallest box on the plane', () => {
    const pending = heightOf(step('a'));
    const held = heightOf(
      step('b', {
        condition: 'Assigned',
        taskState: 'InProgress',
        assignee: { name: 'Ana Neagu' },
      }),
    );

    expect(pending).toBeLessThan(held);
  });

  it('grows with every region a node has facts for', () => {
    const bare = step('a', { condition: 'Assigned', taskState: 'InProgress' });
    const withPerson = { ...bare, assignee: { name: 'Dan Stan' } };
    const withDeadline = { ...withPerson, dueAt: '2026-08-20T09:00:00Z' };
    const withPhases = { ...withDeadline, phases: [{ phase: 'active' as const, seconds: HOUR }] };
    const blocked = { ...withPhases, blockedReason: 'Waiting on the supplier' };

    const heights = [bare, withPerson, withDeadline, withPhases, blocked].map(heightOf);

    for (let index = 1; index < heights.length; index += 1) {
      expect(heights[index] ?? 0).toBeGreaterThan(heights[index - 1] ?? 0);
    }
  });

  it('gives a longer blocker reason more room, because that region may not truncate', () => {
    const short = step('a', { condition: 'Assigned', taskState: 'Blocked', blockedReason: 'Late' });
    const long = step('b', {
      condition: 'Assigned',
      taskState: 'Blocked',
      blockedReason:
        'Waiting on the licence renewal from the supplier, who has not answered two emails',
    });

    expect(heightOf(long)).toBeGreaterThan(heightOf(short));
  });

  it('reserves room for the call to action only where there is one', () => {
    const withoutIt = step('a', { condition: 'Reachable' });
    const withIt = step('b', { condition: 'Reachable', offersAssignment: true });

    expect(heightOf(withIt)).toBeGreaterThan(heightOf(withoutIt));
  });

  it('answers the same height for the same node, every time', () => {
    const node = step('a', {
      condition: 'Assigned',
      taskState: 'Blocked',
      assignee: { name: 'Dan Stan' },
      blockedReason: 'Waiting on the supplier',
    });

    expect(heightOf(node)).toBe(heightOf({ ...node }));
  });
});

const MEASURED_IN_CHROME: Record<string, number> = {
  closed: 206,
  created: 200,
  'in-progress': 230,
  'at-risk': 248,
  blocked: 292,
  overdue: 248,
  'blocked-and-overdue': 292,
  'in-review': 206,
  'sent-back': 268,
  ready: 217,
  pending: 138,
  'former-member': 208,
};

describe('the height model against the browser', () => {
  const entries = galleryEntries((key) => {
    const phrase = key
      .split('.')
      .reduce<unknown>(
        (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
        en as unknown,
      );

    return typeof phrase === 'string' ? phrase : key;
  });

  it('reserves at least as much room as every node actually takes', () => {
    for (const entry of entries) {
      const measured = MEASURED_IN_CHROME[entry.node.id];

      expect(measured, `${entry.node.id} has no recorded measurement`).toBeDefined();
      expect(
        heightOf(entry.node),
        `${entry.node.id} is drawn ${measured}px tall and only ${heightOf(entry.node)}px is reserved`,
      ).toBeGreaterThanOrEqual(measured ?? 0);
    }
  });

  it('does not reserve so much that the plane becomes mostly gap', () => {
    for (const entry of entries) {
      const measured = MEASURED_IN_CHROME[entry.node.id] ?? 0;

      expect(heightOf(entry.node) - measured, `${entry.node.id} over-reserves`).toBeLessThan(80);
    }
  });
});
