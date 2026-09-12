import { describe, expect, it } from 'vitest';

import type { CloseKind } from './bracket';
import type { GraphEdge, GraphNode } from './graph';

import { layoutOf, withinOneHop, type Placement, type Point } from './graphLayout';
import { appearanceOf, elapsedWithPhase } from './nodeAppearance';

function node(id: string, over: Partial<GraphNode> = {}): GraphNode {
  return {
    nodeId: id,
    bracketId: `b-${id}`,
    workType: 'CONTENT',
    activity: null,
    performerName: 'Sara',
    state: 'OPEN',
    elapsed: 3600,
    phase: 'working',
    closeKind: null,
    nodeRole: 'START',
    boundary: false,
    unclaimed: false,

    workTypeOverridden: false,
    department: null,
    markerName: null,
    client: null,
    projectLabel: null,
    text: null,
    title: null,
    detail: null,
    checklist: null,
    direction: null,
    kind: null,
    outputType: null,
    taskTemplateId: null,
    messageId: `m-${id}`,
    conversationId: 'c-1',
    ...over,
  };
}

function xy(placement: Placement, id: string): Point {
  const at = placement[id];

  if (at === undefined) {
    throw new Error(`the layout placed no node for "${id}"`);
  }

  return at;
}

function parentage(from: string, to: string): GraphEdge {
  return { kind: 'PARENTAGE', fromNodeId: from, toNodeId: to };
}

describe('the flow layout', () => {
  it('puts five people marked from one message in one column, not five', () => {
    const boundary = node('b', { boundary: true, workType: 'CLIENT_INTAKE' });
    const siblings = ['content', 'photo', 'design', 'video', 'ads'].map((type) =>
      node(type, { workType: type.toUpperCase() }),
    );

    const placement = layoutOf(
      'FLOW',
      [boundary, ...siblings],
      siblings.map((one) => parentage('b', one.nodeId)),
    );

    const columns = new Set(siblings.map((one) => xy(placement, one.nodeId).x));

    expect(columns.size, 'five siblings share one depth, so they share one column').toBe(1);
    expect(xy(placement, 'content').x, 'and it is one step right of the boundary').toBeGreaterThan(
      xy(placement, 'b').x,
    );

    const rows = new Set(siblings.map((one) => xy(placement, one.nodeId).y));
    expect(rows.size, 'five distinct rows, so none is painted over another').toBe(5);
  });

  it('does not push a bracket rightwards because it is waiting on another', () => {
    const boundary = node('b', { boundary: true });
    const sara = node('sara');
    const maya = node('maya', { state: 'WAITING' });

    const placement = layoutOf(
      'FLOW',
      [boundary, sara, maya],
      [
        parentage('b', 'sara'),
        parentage('b', 'maya'),
        { kind: 'WAIT', fromNodeId: 'sara', toNodeId: 'maya' },
      ],
    );

    expect(xy(placement, 'maya').x, 'a wait is a dependency; depth is parentage alone').toBe(
      xy(placement, 'sara').x,
    );
  });

  it('places a handover successor one step beyond the bracket it continues', () => {
    const placement = layoutOf(
      'FLOW',
      [node('nour'), node('karim')],
      [{ kind: 'SUCCESSION', fromNodeId: 'nour', toNodeId: 'karim' }],
    );

    expect(xy(placement, 'karim').x).toBeGreaterThan(xy(placement, 'nour').x);
  });

  it('terminates on a cycle rather than recursing forever', () => {
    const placement = layoutOf(
      'FLOW',
      [node('one'), node('other')],
      [parentage('one', 'other'), parentage('other', 'one')],
    );

    expect(Object.keys(placement)).toHaveLength(2);
  });

  it('places nothing for an engagement with no nodes, rather than throwing', () => {
    expect(layoutOf('FLOW', [], [])).toEqual({});
  });
});

describe('focus', () => {
  it('keeps a node and everything one hop away, over every kind of edge', () => {
    const near = withinOneHop('maya', [
      parentage('b', 'maya'),
      { kind: 'WAIT', fromNodeId: 'sara', toNodeId: 'maya' },
      parentage('b', 'unrelated'),
    ]);

    expect([...near].sort()).toEqual(['b', 'maya', 'sara']);
  });
});

describe('what a node looks like', () => {
  it.each<[CloseKind, boolean]>([
    ['DELIVERED', true],
    ['DONE', true],
    ['DROPPED', false],
    ['LAPSED', false],
    ['HANDED_OVER', false],
    ['CADENCE_CLOSED', false],
    ['PARENT_CLOSED', false],
    ['OVERRIDE', false],
    ['MERGED', false],
  ])('reads %s as %s', (closeKind, completed) => {
    const seen = appearanceOf(node('one', { state: 'CLOSED', closeKind }));

    expect(seen.completed).toBe(completed);
    expect(seen.state).toBe(completed ? 'COMPLETE' : 'SCAR');
    expect(seen.glyph, '§14 — the colour is never the only carrier').not.toBe('');
    expect(seen.label, 'and neither is the glyph').not.toBe('');
  });

  it('reads the job boundary as a boundary and not as open work', () => {
    expect(appearanceOf(node('b', { boundary: true, state: 'OPEN' })).state).toBe('BOUNDARY');
  });

  it('reads a bracket with an open wait as waiting', () => {
    expect(appearanceOf(node('one', { state: 'WAITING' })).state).toBe('WAITING');
  });

  it('never states an elapsed figure without the phase it was spent in', () => {
    expect(elapsedWithPhase(node('one', { elapsed: 1800, phase: 'working' }))).toBe('30m working');
    expect(elapsedWithPhase(node('one', { elapsed: 7200, phase: 'waiting' }))).toBe('2h waiting');
    expect(elapsedWithPhase(node('one', { elapsed: 60 * 60 * 24 * 5, phase: 'waiting' }))).toBe(
      '5d waiting',
    );
  });

  it('rounds a figure under a minute up rather than reporting zero', () => {
    expect(elapsedWithPhase(node('one', { elapsed: 12, phase: 'working' }))).toBe('1m working');
  });
});

describe('the layouts that are not offered', () => {
  it('groups lanes by work type, never by who did the work', () => {
    const placement = layoutOf(
      'LANES',
      [
        node('one', { workType: 'DESIGN', performerName: 'Karim' }),
        node('other', { workType: 'DESIGN', performerName: 'Nour' }),
        node('third', { workType: 'PHOTO', performerName: 'Karim' }),
      ],
      [],
    );

    expect(xy(placement, 'one').y, 'two people on one work type share a lane').toBe(
      xy(placement, 'other').y,
    );
    expect(xy(placement, 'third').y, 'one person across two work types does not').not.toBe(
      xy(placement, 'one').y,
    );
  });
});
