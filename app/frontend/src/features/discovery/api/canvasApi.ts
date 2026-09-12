import { apiRequest } from '../../../shared/api/client';
import type { Direction, NodeKind, OutputType, TrackState } from './discoveryApi';

export type CanvasPhaseName = 'WORK' | 'EXTERNAL_WAIT' | 'INTERNAL_WAIT' | 'REVIEW';

export interface CanvasPhase {
  phase: CanvasPhaseName;
  ms: number;
}

export interface CanvasCard {
  nodeId: string;

  title: string;
  kind: NodeKind;
  direction: Direction;

  outputType: OutputType | null;

  templated: boolean;
  phases: CanvasPhase[];

  conversationId: string | null;

  messageId: string | null;
}

export interface CanvasLoop {
  memberNodeIds: string[];
  cycleCount: number;

  exitCondition: string | null;
}

export interface CanvasLane {
  trackId: string;

  fromRoleName: string | null;

  toRoleName: string | null;
  state: TrackState;

  completeness: 'COMPLETE' | 'PARTIAL' | 'START_ONLY' | null;

  closeReason: string | null;

  weaklyKeyed: boolean;
  cards: CanvasCard[];
  loops: CanvasLoop[];
}

export interface Canvas {
  jobId: string;
  jobName: string;
  lanes: CanvasLane[];
}

export function fetchCanvas(jobId: string): Promise<Canvas> {
  return apiRequest<Canvas>(`/discovery/canvas?jobId=${encodeURIComponent(jobId)}`);
}

export function moveCardToLane(nodeId: string, trackId: string): Promise<void> {
  return apiRequest<void>(`/discovery/nodes/${encodeURIComponent(nodeId)}/lane`, {
    method: 'PATCH',
    body: JSON.stringify({ trackId }),
  });
}
