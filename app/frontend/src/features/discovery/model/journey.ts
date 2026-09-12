import type { GraphEdge, GraphNode } from './graph';
import { depthsOf } from './graphLayout';

export interface JourneyStep {
  node: GraphNode;

  index: number;

  threadChanged: boolean;
}

export function journeyOf(nodes: readonly GraphNode[], edges: readonly GraphEdge[]): JourneyStep[] {
  const depth = depthsOf(nodes, edges);

  const ordered = [...nodes].sort((one, other) => {
    if (one.boundary !== other.boundary) {
      return one.boundary ? -1 : 1;
    }

    const byDepth = (depth.get(one.nodeId) ?? 0) - (depth.get(other.nodeId) ?? 0);

    if (byDepth !== 0) {
      return byDepth;
    }

    return one.workType.localeCompare(other.workType) || one.nodeId.localeCompare(other.nodeId);
  });

  return ordered.map((node, at) => {
    const before = at === 0 ? undefined : ordered[at - 1];

    return {
      node,
      index: at + 1,

      threadChanged:
        before !== undefined &&
        node.conversationId !== null &&
        before.conversationId !== null &&
        node.conversationId !== before.conversationId,
    };
  });
}

export function lineageOf(nodeId: string, edges: readonly GraphEdge[]): ReadonlySet<string> {
  const up = new Map<string, string[]>();
  const down = new Map<string, string[]>();

  for (const edge of edges) {
    if (edge.kind !== 'PARENTAGE' && edge.kind !== 'SUCCESSION') {
      continue;
    }

    up.set(edge.toNodeId, [...(up.get(edge.toNodeId) ?? []), edge.fromNodeId]);
    down.set(edge.fromNodeId, [...(down.get(edge.fromNodeId) ?? []), edge.toNodeId]);
  }

  const line = new Set<string>([nodeId]);

  for (const direction of [up, down]) {
    const stack = [nodeId];

    while (stack.length > 0) {
      const at = stack.pop() as string;

      for (const next of direction.get(at) ?? []) {
        if (!line.has(next)) {
          line.add(next);
          stack.push(next);
        }
      }
    }
  }

  return line;
}
