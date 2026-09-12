import dagre from 'dagre';

import type { CanvasNode } from './node';

export const NODE_WIDTH = 228;

export const COLUMN_GAP = 76;
export const ROW_GAP = 40;

function compare(one: string, other: string): number {
  return one < other ? -1 : one > other ? 1 : 0;
}

function byIdentifier(one: { id: string }, other: { id: string }): number {
  return compare(one.id, other.id);
}

export interface GraphEdge {
  from: string;
  to: string;

  satisfied: boolean;
}

export interface PlacedNode {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;

  depth: number;
}

const HEADER_PADDING = 22;
const EYEBROW = 22;
const TITLE_LINE = 19;
const TITLE_CHARS_PER_LINE = 22;
const STATUS_BAND = 38;
const RISK_CHIP_ROW = 26;
const STRIP_PADDING = 30;
const STRIP_LINE = 17;
const STRIP_CHARS_PER_LINE = 28;
const REGION_PADDING = 22;
const PERSON_ROW = 30;
const META_ROW = 22;
const PHASE_BAR = 14;
const PHASE_WORDS = 18;
const CALL_TO_ACTION = 36;

export function depthFromEdges(
  steps: readonly CanvasNode[],
  edges: readonly GraphEdge[],
): Map<string, number> {
  const known = new Set(steps.map((step) => step.id));
  const waitsOn = new Map<string, string[]>();

  for (const edge of edges) {
    if (known.has(edge.from) && known.has(edge.to)) {
      waitsOn.set(edge.to, [...(waitsOn.get(edge.to) ?? []), edge.from]);
    }
  }

  const depths = new Map<string, number>();

  const depthOf = (id: string, seen: Set<string>): number => {
    const already = depths.get(id);

    if (already !== undefined) {
      return already;
    }

    if (seen.has(id)) {
      return 0;
    }

    const parents = waitsOn.get(id) ?? [];
    const depth =
      parents.length === 0
        ? 0
        : Math.max(...parents.map((parent) => depthOf(parent, new Set([...seen, id])) + 1));

    depths.set(id, depth);
    return depth;
  };

  for (const step of [...steps].sort(byIdentifier)) {
    depthOf(step.id, new Set());
  }

  return depths;
}

export interface EdgeStroke {
  stroke: string;
  strokeWidth: number;
  strokeDasharray?: string;
}

export function edgeStroke(edge: GraphEdge): EdgeStroke {
  return edge.satisfied
    ? { stroke: 'var(--line-strong)', strokeWidth: 1.6 }
    : { stroke: 'var(--faint)', strokeWidth: 1.4, strokeDasharray: '5 5' };
}

function lines(text: string, charsPerLine: number, ceiling = Number.MAX_SAFE_INTEGER): number {
  return Math.min(ceiling, Math.max(1, Math.ceil(text.length / charsPerLine)));
}

export function heightOf(node: CanvasNode): number {
  const pending = node.condition === 'Pending';
  const strip = node.blockedReason ?? node.reworkNote;

  let height = HEADER_PADDING + lines(node.title, TITLE_CHARS_PER_LINE, 2) * TITLE_LINE;

  if (node.stepNumber !== undefined) {
    height += EYEBROW;
  }

  height += STATUS_BAND;

  if (!pending && (node.atRisk === true || (node.dueAt !== undefined && node.dueAt !== null))) {
    height += RISK_CHIP_ROW;
  }

  if (strip !== undefined) {
    height += STRIP_PADDING + lines(strip, STRIP_CHARS_PER_LINE) * STRIP_LINE;
  }

  const assignee = pending ? undefined : node.assignee;
  const deadline = pending ? undefined : node.dueAt;
  const phases = pending ? undefined : node.phases;
  const metaRows = (deadline === undefined || deadline === null ? 0 : 1) + (node.meta?.length ?? 0);

  if (assignee !== undefined || deadline != null || node.meta !== undefined) {
    height += REGION_PADDING + (assignee === undefined ? 0 : PERSON_ROW) + metaRows * META_ROW;
  }

  if (phases !== undefined && phases.length > 0) {
    height += REGION_PADDING + PHASE_BAR + Math.ceil(phases.length / 2) * PHASE_WORDS;
  }

  if (node.offersAssignment === true) {
    height += REGION_PADDING + CALL_TO_ACTION;
  }

  return height;
}

export function layoutGraph(
  steps: readonly CanvasNode[],
  edges: readonly GraphEdge[],
): PlacedNode[] {
  const depths = depthFromEdges(steps, edges);
  const known = new Set(steps.map((step) => step.id));

  const graph = new dagre.graphlib.Graph();
  graph.setGraph({ rankdir: 'LR', ranksep: COLUMN_GAP, nodesep: ROW_GAP });
  graph.setDefaultEdgeLabel(() => ({}));

  for (const step of [...steps].sort(byIdentifier)) {
    graph.setNode(step.id, { width: NODE_WIDTH, height: heightOf(step) });
  }

  for (const edge of [...edges].sort((a, b) => compare(`${a.from}>${a.to}`, `${b.from}>${b.to}`))) {
    if (known.has(edge.from) && known.has(edge.to)) {
      graph.setEdge(edge.from, edge.to);
    }
  }

  dagre.layout(graph);

  const ordered = [...steps]
    .map((step) => ({
      step,
      depth: depths.get(step.id) ?? 0,
      order: (graph.node(step.id) as { y: number } | undefined)?.y ?? 0,
    }))
    .sort((a, b) => a.depth - b.depth || a.order - b.order || compare(a.step.id, b.step.id));

  const stacked = new Map<number, number>();
  const placed: PlacedNode[] = ordered.map(({ step, depth }) => {
    const height = heightOf(step);
    const top = stacked.get(depth) ?? 0;

    stacked.set(depth, top + height + ROW_GAP);

    return {
      id: step.id,
      x: depth * (NODE_WIDTH + COLUMN_GAP),
      y: top,
      width: NODE_WIDTH,
      height,
      depth,
    };
  });

  const tallest = Math.max(0, ...[...stacked.values()].map((bottom) => bottom - ROW_GAP));

  return placed.map((node) => {
    const column = (stacked.get(node.depth) ?? ROW_GAP) - ROW_GAP;

    return { ...node, y: node.y + (tallest - column) / 2 };
  });
}

export function edgeLabel(edge: GraphEdge, steps: readonly CanvasNode[], t: Translate): string {
  const source = steps.find((step) => step.id === edge.from);

  if (edge.satisfied) {
    return t('canvas.edge.satisfied');
  }

  if (source?.blockedReason !== undefined) {
    return t('canvas.edge.blocked');
  }

  if (source?.condition === 'Pending') {
    return t('canvas.edge.pending');
  }

  return t('canvas.edge.inProgress');
}

export type Translate = (key: string, values?: Record<string, string | number>) => string;

export interface DrawnEdge {
  id: string;
  source: string;
  target: string;
  style: EdgeStroke;
  markerStart: string;
  markerEnd: string;
  label: string;
}

export function drawnEdges(
  steps: readonly CanvasNode[],
  edges: readonly GraphEdge[],
  t: Translate,
  markers: { met: string; unmet: string },
): DrawnEdge[] {
  const known = new Set(steps.map((step) => step.id));

  return edges
    .filter((edge) => known.has(edge.from) && known.has(edge.to))
    .map((edge) => ({
      id: `${edge.from}->${edge.to}`,
      source: edge.from,
      target: edge.to,
      style: edgeStroke(edge),
      markerStart: edge.satisfied ? markers.met : markers.unmet,
      markerEnd: edge.satisfied ? markers.met : markers.unmet,
      label: edgeLabel(edge, steps, t),
    }));
}
