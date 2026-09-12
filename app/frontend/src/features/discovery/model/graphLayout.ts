import type { GraphEdge, GraphNode } from './graph';

export interface Point {
  x: number;
  y: number;
}

export type Placement = Readonly<Record<string, Point>>;

export type LayoutView = 'FLOW' | 'CLUSTERS' | 'LANES';

export const NODE_WIDTH = 232;
export const NODE_HEIGHT = 188;

const COLUMN_GAP = 316;
const ROW_GAP = 232;
const ORIGIN = { x: 120, y: 120 };

export function layoutOf(
  view: LayoutView,
  nodes: readonly GraphNode[],
  edges: readonly GraphEdge[],
): Placement {
  if (nodes.length === 0) {
    return {};
  }

  switch (view) {
    case 'CLUSTERS':
      return byGroup(nodes, (node) => node.workType, 3);
    case 'LANES':
      return byGroup(nodes, (node) => node.workType, Number.MAX_SAFE_INTEGER);
    case 'FLOW':
    default:
      return byDepth(nodes, edges);
  }
}

function byDepth(nodes: readonly GraphNode[], edges: readonly GraphEdge[]): Placement {
  const depth = depthsOf(nodes, edges);
  const columns = new Map<number, GraphNode[]>();

  for (const node of nodes) {
    const at = depth.get(node.nodeId) ?? 0;
    const column = columns.get(at) ?? [];

    column.push(node);
    columns.set(at, column);
  }

  const placed: Record<string, Point> = {};

  for (const [at, column] of columns) {
    const ordered = [...column].sort((one, other) => one.workType.localeCompare(other.workType));
    const height = (ordered.length - 1) * ROW_GAP;

    ordered.forEach((node, index) => {
      placed[node.nodeId] = {
        x: ORIGIN.x + at * COLUMN_GAP,
        y: ORIGIN.y + index * ROW_GAP - height / 2 + 400,
      };
    });
  }

  return placed;
}

export function depthsOf(
  nodes: readonly GraphNode[],
  edges: readonly GraphEdge[],
): Map<string, number> {
  const parents = new Map<string, string[]>();

  for (const edge of edges) {
    if (edge.kind !== 'PARENTAGE' && edge.kind !== 'SUCCESSION') {
      continue;
    }

    parents.set(edge.toNodeId, [...(parents.get(edge.toNodeId) ?? []), edge.fromNodeId]);
  }

  const depth = new Map<string, number>(nodes.map((node) => [node.nodeId, 0]));

  for (let pass = 0; pass < nodes.length; pass += 1) {
    let moved = false;

    for (const node of nodes) {
      const from = parents.get(node.nodeId) ?? [];

      if (from.length === 0) {
        continue;
      }

      const below = Math.max(...from.map((parent) => depth.get(parent) ?? 0)) + 1;

      if (below > (depth.get(node.nodeId) ?? 0)) {
        depth.set(node.nodeId, below);
        moved = true;
      }
    }

    if (!moved) {
      break;
    }
  }

  return depth;
}

function byGroup(
  nodes: readonly GraphNode[],
  keyOf: (node: GraphNode) => string,
  perRow: number,
): Placement {
  const groups = new Map<string, GraphNode[]>();

  for (const node of nodes) {
    const key = keyOf(node);

    groups.set(key, [...(groups.get(key) ?? []), node]);
  }

  const placed: Record<string, Point> = {};

  const ordered = [...groups.entries()].sort((one, other) => one[0].localeCompare(other[0]));

  let row = 0;

  for (const [, members] of ordered) {
    const rows = Math.ceil(members.length / Math.min(perRow, members.length || 1));

    members.forEach((node, index) => {
      placed[node.nodeId] = {
        x: ORIGIN.x + (index % perRow) * COLUMN_GAP,
        y: ORIGIN.y + (row + Math.floor(index / perRow)) * ROW_GAP,
      };
    });

    row += rows + 1;
  }

  return placed;
}

export function extentOf(placement: Placement): {
  x: number;
  y: number;
  width: number;
  height: number;
} {
  const points = Object.values(placement);

  if (points.length === 0) {
    return { x: 0, y: 0, width: NODE_WIDTH, height: NODE_HEIGHT };
  }

  const left = Math.min(...points.map((point) => point.x));
  const top = Math.min(...points.map((point) => point.y));
  const right = Math.max(...points.map((point) => point.x)) + NODE_WIDTH;
  const bottom = Math.max(...points.map((point) => point.y)) + NODE_HEIGHT;

  return { x: left, y: top, width: right - left, height: bottom - top };
}

export function withinOneHop(nodeId: string, edges: readonly GraphEdge[]): ReadonlySet<string> {
  const near = new Set<string>([nodeId]);

  for (const edge of edges) {
    if (edge.fromNodeId === nodeId) {
      near.add(edge.toNodeId);
    } else if (edge.toNodeId === nodeId) {
      near.add(edge.fromNodeId);
    }
  }

  return near;
}
