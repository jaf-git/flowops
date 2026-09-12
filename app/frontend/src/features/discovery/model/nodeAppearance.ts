import { describeEnding } from './bracket';
import type { GraphNode } from './graph';

export type RenderState = 'BOUNDARY' | 'OPEN' | 'WAITING' | 'COMPLETE' | 'SCAR' | 'OUTSIDE';

export interface NodeAppearance {
  state: RenderState;

  glyph: string;

  label: string;

  completed: boolean;
}

type AppearanceFacts = Pick<GraphNode, 'boundary' | 'closeKind' | 'state'>;

export function appearanceOf(node: AppearanceFacts): NodeAppearance {
  if (node.boundary) {
    return { state: 'BOUNDARY', glyph: '◆', label: 'Job boundary', completed: false };
  }

  if (node.closeKind !== null) {
    const ending = describeEnding(node.closeKind);

    return {
      state: ending.completed ? 'COMPLETE' : 'SCAR',
      glyph: ending.completed ? '✓' : '—',
      label: ending.label,
      completed: ending.completed,
    };
  }

  if (node.state === 'WAITING') {
    return { state: 'WAITING', glyph: '◇', label: 'Waiting', completed: false };
  }

  return { state: 'OPEN', glyph: '◆', label: 'Open', completed: false };
}

export function elapsedWithPhase(node: Pick<GraphNode, 'elapsed' | 'phase'>): string {
  const minutes = Math.floor(node.elapsed / 60);

  if (minutes < 60) {
    return `${String(Math.max(minutes, 1))}m ${node.phase}`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 48) {
    return `${String(hours)}h ${node.phase}`;
  }

  return `${String(Math.floor(hours / 24))}d ${node.phase}`;
}

export function performerOf(node: Pick<GraphNode, 'performerName'>): string {
  return node.performerName ?? 'Unclaimed';
}
