import type { InstanceStepPayload } from '../model/fromInstance';
import type { GraphEdge } from '../model/layout';
import type { CanvasNode } from '../model/node';

export interface OperationsCanvasData {
  name: string;
  progress: { closed: number; total: number };
  steps: CanvasNode[];
  edges: GraphEdge[];

  raw: InstanceStepPayload[];
}
