import type { CloseKind } from './bracket';

export type EdgeKind = 'PARENTAGE' | 'WAIT' | 'SUCCESSION';

export type NodeRole = 'START' | 'WORK' | 'END';

export interface GraphNode {
  nodeId: string;
  bracketId: string;
  workType: string;

  workTypeOverridden: boolean;

  department: string | null;
  performerName: string | null;

  markerName: string | null;

  client: string | null;

  projectLabel: string | null;
  state: string;

  elapsed: number;

  phase: string;

  closeKind: CloseKind | null;
  nodeRole: NodeRole | null;

  boundary: boolean;

  unclaimed: boolean;

  messageId: string | null;
  conversationId: string | null;

  text: string | null;

  title: string | null;

  detail: string | null;

  checklist: readonly string[] | null;

  direction: string | null;
  kind: string | null;

  outputType: string | null;

  taskTemplateId: string | null;

  /** What the person called this piece of work, where they named one. Null is an ordinary mark. */
  activity: string | null;
}

export interface GraphEdge {
  kind: EdgeKind;
  fromNodeId: string;
  toNodeId: string;
}

export interface JobGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface JobHeader {
  jobId: string;
  name: string;

  client: string | null;

  project: string | null;

  status: string;

  closerName: string | null;

  liveBrackets: number;

  totalBrackets: number;

  closeReason: string | null;

  shapeEligible: boolean;
}

export interface ClientArtifact {
  artifactId: string;
  bracketId: string;
  workType: string;

  kind: 'TEXT' | 'LINK' | 'MESSAGE_REF';
  publishedAt: string;
  value: string | null;
  readable: boolean;
}

export interface RailMark {
  bracketId: string;
  jobId: string;
  workType: string;
  performerName: string | null;

  state: string;

  conversationId: string;

  messageId: string | null;
}

export interface RailLane {
  jobId: string;
  client: string | null;
  project: string | null;
  jobName: string;
  marks: RailMark[];
}

export interface TrackNode {
  nodeId: string;
  kind: 'START' | 'WORK' | 'END';
  messageId: string | null;
}

export interface TrackLine {
  bracketId: string;

  conversationId: string;

  address: string;
  workType: string;
  state: string;
  closeKind: string | null;
  nodes: TrackNode[];
}

export interface MyWorkCounts {
  waitingOnYou: number;

  openWork: number;

  deliveredThisWeek: number;
}
