import {
  Background,
  BackgroundVariant,
  Handle,
  Position,
  ReactFlow,
  useNodesState,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react';
import { useEffect, useMemo, useRef, useState, type FocusEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { drawnEdges, layoutGraph, NODE_WIDTH, type GraphEdge } from '../model/layout';
import type { CanvasNode } from '../model/node';
import { NodeOverviewCard } from './NodeOverviewCard';
import { TaskNodeCard } from './TaskNodeCard';

import '@xyflow/react/dist/style.css';

function Anchor({
  type,
  position,
}: {
  type: 'source' | 'target';
  position: Position;
}): JSX.Element {
  return (
    <Handle
      type={type}
      position={position}
      isConnectable={false}
      style={{ opacity: 0, pointerEvents: 'none', width: 1, height: 1, minWidth: 1, minHeight: 1 }}
    />
  );
}

type StepNodeData = {
  step: CanvasNode;
  now: Date;
  onSelect?: (id: string) => void;
  onAssign?: (id: string) => void;
};

function StepNode({ data, selected }: NodeProps<Node<StepNodeData>>): JSX.Element {
  const [looking, setLooking] = useState(false);
  const cardId = `overview-${data.step.id}`;

  const leave = (event: FocusEvent<HTMLDivElement>): void => {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setLooking(false);
    }
  };

  return (
    <div
      style={{ position: 'relative' }}
      onMouseEnter={() => setLooking(true)}
      onMouseLeave={() => setLooking(false)}
      onFocus={() => setLooking(true)}
      onBlur={leave}
    >
      <Anchor type="target" position={Position.Left} />
      <TaskNodeCard
        node={data.step}
        now={data.now}
        selected={selected}
        onSelect={data.onSelect}
        onAssign={data.onAssign === undefined ? undefined : () => data.onAssign?.(data.step.id)}
        describedBy={looking ? cardId : undefined}
      />
      <Anchor type="source" position={Position.Right} />

      {looking && (
        <div
          style={{
            position: 'absolute',
            left: '100%',
            top: 0,
            marginLeft: 'var(--space-3)',
            zIndex: 10,
          }}
        >
          <NodeOverviewCard id={cardId} node={data.step} now={data.now} />
        </div>
      )}
    </div>
  );
}

const NODE_TYPES = { step: StepNode };

export const DOT_MET = 'canvas-dot-met';
export const DOT_UNMET = 'canvas-dot-unmet';

export interface ProcessGraphCanvasProps {
  steps: readonly CanvasNode[];
  edges: readonly GraphEdge[];

  now?: Date;
  selected?: string;
  onSelect?: (id: string) => void;

  onAssign?: (id: string) => void;
}

export function ProcessGraphCanvas({
  steps,
  edges,
  now,
  selected,
  onSelect,
  onAssign,
}: ProcessGraphCanvasProps): JSX.Element {
  const { t } = useTranslation();

  const reference = useMemo(() => now ?? new Date(), [now]);

  const computed = useMemo<Node<StepNodeData>[]>(() => {
    const placed = layoutGraph(steps, edges);

    return placed.map((position) => {
      const step = steps.find((candidate) => candidate.id === position.id);

      return {
        id: position.id,
        type: 'step',
        position: { x: position.x, y: position.y },
        data: { step: step as CanvasNode, now: reference, onSelect, onAssign },
        selected: position.id === selected,

        draggable: true,
        connectable: false,
        width: NODE_WIDTH,
        height: position.height,

        measured: { width: NODE_WIDTH, height: position.height },
      };
    });
  }, [steps, edges, reference, selected, onSelect, onAssign]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node<StepNodeData>>(computed);

  const signature = useMemo(
    () =>
      `${steps.map((step) => step.id).join(',')}|${edges.map((edge) => `${edge.from}>${edge.to}`).join(',')}`,
    [steps, edges],
  );
  const laidOutFor = useRef(signature);

  useEffect(() => {
    if (laidOutFor.current !== signature) {
      laidOutFor.current = signature;
      setNodes(computed);
      return;
    }

    setNodes((current: Node<StepNodeData>[]) => {
      const moved = new Map(current.map((node) => [node.id, node.position]));
      return computed.map((node) => ({ ...node, position: moved.get(node.id) ?? node.position }));
    });
  }, [computed, signature, setNodes]);

  const drawn = useMemo<Edge[]>(
    () =>
      drawnEdges(steps, edges, t, { met: DOT_MET, unmet: DOT_UNMET }).map((edge) => ({
        ...edge,
        labelShowBg: true,
        labelBgPadding: [6, 3] as [number, number],
        labelBgBorderRadius: 999,
        labelBgStyle: { fill: 'var(--surface)', stroke: 'var(--line)' },
        labelStyle: { fill: 'var(--muted)', fontSize: 11, fontWeight: 500 },
      })),
    [edges, steps, t],
  );

  if (steps.length === 0) {
    return (
      <div
        className="canvas-plane"
        data-testid="canvas-plane-empty"
        style={{
          height: '100%',
          display: 'grid',
          placeItems: 'center',
          textAlign: 'center',
          padding: 'var(--space-6)',
        }}
      >
        <p style={{ margin: 0, color: 'var(--muted)', maxWidth: '40ch' }}>
          {t('canvas.instance.empty')}
        </p>
      </div>
    );
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={drawn}
      nodeTypes={NODE_TYPES}
      onNodesChange={onNodesChange}
      onNodeClick={(_, node) => onSelect?.(node.id)}
      nodesConnectable={false}

      nodesDraggable
      edgesFocusable={false}
      edgesReconnectable={false}
      elementsSelectable
      proOptions={{ hideAttribution: false }}
      fitView
      minZoom={0.3}
      maxZoom={1.5}

      nodesFocusable
    >
      <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true">
        <defs>
          <marker
            id="canvas-dot-met"
            viewBox="0 0 8 8"
            markerWidth="8"
            markerHeight="8"
            refX="4"
            refY="4"
          >
            <circle cx="4" cy="4" r="3" fill="var(--line-strong)" />
          </marker>
          <marker
            id="canvas-dot-unmet"
            viewBox="0 0 8 8"
            markerWidth="8"
            markerHeight="8"
            refX="4"
            refY="4"
          >
            <circle
              cx="4"
              cy="4"
              r="2.5"
              fill="var(--surface)"
              stroke="var(--line)"
              strokeWidth="1.2"
            />
          </marker>
        </defs>
      </svg>
      <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="var(--canvas-grid)" />
    </ReactFlow>
  );
}
