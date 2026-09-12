import { describe, expect, it } from 'vitest';

import type { GraphEdge, GraphNode } from './graph';

import { journeyOf, lineageOf } from './journey';

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

function parentage(from: string, to: string): GraphEdge {
  return { kind: 'PARENTAGE', fromNodeId: from, toNodeId: to };
}

describe('the order the chronology states', () => {
  it('leads with the boundary even when its depth would put it elsewhere', () => {
    const nodes = [
      node('content'),
      node('design'),

      node('boundary', { boundary: true, workType: 'ZZZ_LAST_ALPHABETICALLY' }),
    ];
    const edges = [parentage('content', 'design'), parentage('design', 'boundary')];

    expect(journeyOf(nodes, edges).map((step) => step.node.nodeId)).toEqual([
      'boundary',
      'content',
      'design',
    ]);
  });

  it('numbers the steps from one, in the order it presents them', () => {
    const nodes = [node('one'), node('two'), node('three')];

    expect(journeyOf(nodes, [parentage('one', 'two'), parentage('two', 'three')])).toMatchObject([
      { node: { nodeId: 'one' }, index: 1 },
      { node: { nodeId: 'two' }, index: 2 },
      { node: { nodeId: 'three' }, index: 3 },
    ]);
  });

  it('orders by descent, so work that followed other work comes after it', () => {
    const nodes = [node('third'), node('first'), node('second')];
    const edges = [parentage('first', 'second'), parentage('second', 'third')];

    expect(journeyOf(nodes, edges).map((step) => step.node.nodeId)).toEqual([
      'first',
      'second',
      'third',
    ]);
  });

  it('gives the same order for the same graph twice over', () => {
    const nodes = ['ads', 'content', 'design', 'photo', 'video'].map((type) =>
      node(type, { workType: type.toUpperCase() }),
    );
    const edges = nodes.map((one) => parentage('boundary', one.nodeId));

    const once = journeyOf([node('boundary', { boundary: true }), ...nodes], edges);
    const again = journeyOf([node('boundary', { boundary: true }), ...[...nodes].reverse()], edges);

    expect(again.map((step) => step.node.nodeId)).toEqual(once.map((step) => step.node.nodeId));
  });

  it('separates two steps of one work type by node, so neither is dropped from the order', () => {
    const nodes = [node('later', { workType: 'DESIGN' }), node('earlier', { workType: 'DESIGN' })];

    expect(journeyOf(nodes, []).map((step) => step.node.nodeId)).toEqual(['earlier', 'later']);
  });
});

describe('the thread changing between two steps', () => {
  it('marks the step where the conversation differs from the one before it', () => {
    const nodes = [
      node('first', { conversationId: 'c-1' }),
      node('second', { conversationId: 'c-2' }),
      node('third', { conversationId: 'c-2' }),
    ];
    const edges = [parentage('first', 'second'), parentage('second', 'third')];

    expect(journeyOf(nodes, edges).map((step) => step.threadChanged)).toEqual([false, true, false]);
  });

  it('never marks the first step, which has nothing before it to differ from', () => {
    expect(journeyOf([node('only', { conversationId: 'c-9' })], [])).toMatchObject([
      { threadChanged: false },
    ]);
  });

  it('says nothing when either side has no conversation at all', () => {
    const nodes = [
      node('first', { conversationId: 'c-1' }),
      node('second', { conversationId: null }),
      node('third', { conversationId: 'c-3' }),
    ];
    const edges = [parentage('first', 'second'), parentage('second', 'third')];

    expect(journeyOf(nodes, edges).map((step) => step.threadChanged)).toEqual([
      false,
      false,
      false,
    ]);
  });
});

describe('the line of descent', () => {
  it('walks up to the ancestors and down to the descendants, not one hop', () => {
    const edges = [
      parentage('grandparent', 'parent'),
      parentage('parent', 'here'),
      parentage('here', 'child'),
      parentage('child', 'grandchild'),
    ];

    expect([...lineageOf('here', edges)].sort()).toEqual([
      'child',
      'grandchild',
      'grandparent',
      'here',
      'parent',
    ]);
  });

  it('leaves out a sibling, which shares an ancestor and is not on the line', () => {
    const edges = [parentage('parent', 'here'), parentage('parent', 'sibling')];

    expect(lineageOf('here', edges).has('sibling')).toBe(false);
  });

  it('does not follow a wait, which is a dependency rather than a descent', () => {
    const edges: GraphEdge[] = [
      { kind: 'WAIT', fromNodeId: 'here', toNodeId: 'blocked-on' },
      parentage('here', 'child'),
    ];

    expect([...lineageOf('here', edges)].sort()).toEqual(['child', 'here']);
  });

  it('follows a succession, which is one bracket carrying on as another', () => {
    const edges: GraphEdge[] = [{ kind: 'SUCCESSION', fromNodeId: 'here', toNodeId: 'carried-on' }];

    expect(lineageOf('here', edges).has('carried-on')).toBe(true);
  });

  it('terminates on a cycle rather than exhausting the stack', () => {
    const edges = [parentage('a', 'b'), parentage('b', 'c'), parentage('c', 'a')];

    expect([...lineageOf('a', edges)].sort()).toEqual(['a', 'b', 'c']);
  });

  it('returns the node itself when it stands alone', () => {
    expect([...lineageOf('lonely', [])]).toEqual(['lonely']);
  });
});
