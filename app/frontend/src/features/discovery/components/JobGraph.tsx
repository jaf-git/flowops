import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type JSX,
  type PointerEvent,
  type WheelEvent,
} from 'react';

import type { GraphEdge, GraphNode as GraphNodeData } from '../api/jobGraphApi';
import {
  extentOf,
  layoutOf,
  withinOneHop,
  NODE_HEIGHT,
  NODE_WIDTH,
  type LayoutView,
  type Point,
  type Placement,
} from '../model/graphLayout';

import { GraphNode } from './GraphNode';

interface JobGraphProps {
  readonly nodes: readonly GraphNodeData[];
  readonly edges: readonly GraphEdge[];
  readonly view: LayoutView;

  readonly focus: boolean;
  readonly selected: string | null;
  readonly onSelect: (nodeId: string | null) => void;
}

interface Camera {
  x: number;
  y: number;
  k: number;
}

const ZOOM_LIMIT = { min: 0.25, max: 2 };
const FIT_PAD = 80;

const GROUND_KEY = 'flowops.graph.ground';

function rememberedGround(): boolean {
  try {
    return window.localStorage.getItem(GROUND_KEY) === 'light';
  } catch {
    return false;
  }
}

export function JobGraph({
  nodes,
  edges,
  view,
  focus,
  selected,
  onSelect,
}: JobGraphProps): JSX.Element {
  const viewport = useRef<HTMLDivElement>(null);
  const world = useRef<HTMLDivElement>(null);
  const camera = useRef<Camera>({ x: 0, y: 0, k: 1 });
  const pan = useRef<{ x: number; y: number; from: Camera } | null>(null);

  const [zoom, setZoom] = useState(1);
  const [light, setLight] = useState(rememberedGround);

  const [nudges, setNudges] = useState<Record<string, Point>>({});
  const nodeDrag = useRef<{ id: string; x: number; y: number; from: Point; moved: boolean } | null>(
    null,
  );

  const [nudgedFor, setNudgedFor] = useState(view);

  if (nudgedFor !== view) {
    setNudgedFor(view);
    setNudges({});
  }

  const placement: Placement = useMemo(() => layoutOf(view, nodes, edges), [view, nodes, edges]);

  const positions: Placement = useMemo(() => {
    if (Object.keys(nudges).length === 0) {
      return placement;
    }

    const moved: Record<string, Point> = { ...placement };

    for (const [id, by] of Object.entries(nudges)) {
      const laid = placement[id];

      if (laid !== undefined) {
        moved[id] = { x: laid.x + by.x, y: laid.y + by.y };
      }
    }

    return moved;
  }, [placement, nudges]);

  const paint = useCallback(() => {
    const at = camera.current;

    if (world.current !== null) {
      world.current.style.transform = `translate(${String(at.x)}px, ${String(at.y)}px) scale(${String(at.k)})`;
    }
  }, []);

  const fit = useCallback(() => {
    const box = viewport.current?.getBoundingClientRect();

    if (box === undefined || Object.keys(placement).length === 0) {
      return;
    }

    const extent = extentOf(placement);
    const width = extent.width + FIT_PAD * 2;
    const height = extent.height + FIT_PAD * 2;
    const k = Math.max(ZOOM_LIMIT.min, Math.min(box.width / width, box.height / height, 1));

    camera.current = {
      k,
      x: (box.width - width * k) / 2 - (extent.x - FIT_PAD) * k,
      y: (box.height - height * k) / 2 - (extent.y - FIT_PAD) * k,
    };

    setZoom(k);
    paint();
  }, [placement, paint]);

  useEffect(() => {
    fit();
  }, [fit]);

  const zoomAt = useCallback(
    (factor: number, anchor?: { x: number; y: number }) => {
      const box = viewport.current?.getBoundingClientRect();
      const at = camera.current;
      const next = Math.max(ZOOM_LIMIT.min, Math.min(ZOOM_LIMIT.max, at.k * factor));

      if (box !== undefined) {
        const cx = anchor === undefined ? box.width / 2 : anchor.x - box.left;
        const cy = anchor === undefined ? box.height / 2 : anchor.y - box.top;

        camera.current = {
          k: next,
          x: cx - (cx - at.x) * (next / at.k),
          y: cy - (cy - at.y) * (next / at.k),
        };
      } else {
        camera.current = { ...at, k: next };
      }

      setZoom(next);
      paint();
    },
    [paint],
  );

  const zoomBy = useCallback((factor: number) => zoomAt(factor), [zoomAt]);

  function wheelZoom(event: WheelEvent<HTMLDivElement>): void {
    const perLine = event.deltaMode === 1 ? 16 : 1;
    const step = Math.max(-120, Math.min(120, event.deltaY * perLine));

    zoomAt(Math.exp(-step / 400), { x: event.clientX, y: event.clientY });
  }

  function startPan(event: PointerEvent<HTMLDivElement>): void {
    const onNode =
      event.target instanceof Element ? event.target.closest<HTMLElement>('.fo-gnode') : null;

    if (onNode !== null) {
      const id = onNode.dataset['nodeId'];

      if (id !== undefined) {
        nodeDrag.current = {
          id,
          x: event.clientX,
          y: event.clientY,
          from: nudges[id] ?? { x: 0, y: 0 },
          moved: false,
        };
        event.currentTarget.setPointerCapture(event.pointerId);
      }

      return;
    }

    if (event.target instanceof Element && event.target.closest('button, a, [role="button"]')) {
      return;
    }

    pan.current = { x: event.clientX, y: event.clientY, from: { ...camera.current } };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function movePan(event: PointerEvent<HTMLDivElement>): void {
    const dragging = nodeDrag.current;

    if (dragging !== null) {
      const k = camera.current.k;
      const dx = (event.clientX - dragging.x) / k;
      const dy = (event.clientY - dragging.y) / k;

      if (!dragging.moved && Math.hypot(dx * k, dy * k) < 3) {
        return;
      }

      dragging.moved = true;
      setNudges((at) => ({
        ...at,
        [dragging.id]: { x: dragging.from.x + dx, y: dragging.from.y + dy },
      }));

      return;
    }

    const from = pan.current;

    if (from === null) {
      return;
    }

    camera.current = {
      ...camera.current,
      x: from.from.x + (event.clientX - from.x),
      y: from.from.y + (event.clientY - from.y),
    };
    paint();
  }

  function endPan(event: PointerEvent<HTMLDivElement>): void {
    if (nodeDrag.current !== null) {
      const { id, moved } = nodeDrag.current;

      nodeDrag.current = null;

      try {
        event.currentTarget.releasePointerCapture(event.pointerId);
      } catch {}

      if (!moved) {
        onSelect(id);
      }

      return;
    }

    if (pan.current === null) {
      return;
    }

    pan.current = null;
    event.currentTarget.releasePointerCapture(event.pointerId);
  }

  const near = selected === null ? null : withinOneHop(selected, edges);

  function dimOf(nodeId: string): number {
    if (!focus || near === null || near.has(nodeId)) {
      return 1;
    }

    return light ? 0.24 : 0.18;
  }

  function chooseGround(toLight: boolean): void {
    setLight(toLight);

    try {
      window.localStorage.setItem(GROUND_KEY, toLight ? 'light' : 'dark');
    } catch {}
  }

  return (
    <div className={light ? 'fo-graph' : 'fo-graph fo-spatial'}>
      <div
        ref={viewport}
        className="fo-graph-viewport"
        onWheel={wheelZoom}
        onPointerDown={startPan}
        onPointerMove={movePan}
        onPointerUp={endPan}
        onPointerCancel={endPan}

        role="presentation"
      >
        <div ref={world} className="fo-graph-world">
          <svg className="fo-graph-edges" aria-hidden="true" focusable="false">
            {edges.map((edge) => (
              <Edge
                key={`${edge.kind}-${edge.fromNodeId}-${edge.toNodeId}`}
                edge={edge}
                placement={positions}
                lit={
                  selected !== null && (edge.fromNodeId === selected || edge.toNodeId === selected)
                }
                dim={
                  focus && near !== null && !(near.has(edge.fromNodeId) && near.has(edge.toNodeId))
                }
                dimTo={light ? 0.09 : 0.06}
              />
            ))}
          </svg>

          {nodes.map((node) => {
            const at = positions[node.nodeId];

            return at === undefined ? null : (
              <GraphNode
                key={node.nodeId}
                node={node}
                at={at}
                selected={selected === node.nodeId}
                dim={dimOf(node.nodeId)}
                onSelect={onSelect}
              />
            );
          })}
        </div>
      </div>

      <div className="fo-graph-tools">
        <button
          type="button"
          className="ui-button ui-button-quiet"
          aria-pressed={light}
          onClick={() => {
            chooseGround(!light);
          }}
        >
          {light ? 'Dark' : 'Light'}
        </button>
        <button type="button" className="ui-button ui-button-quiet" onClick={fit}>
          Fit
        </button>
        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => {
            zoomBy(1 / 1.25);
          }}
          aria-label="Zoom out"
        >
          −
        </button>
        <span className="fo-graph-zoom">{`${String(Math.round(zoom * 100))}%`}</span>
        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => {
            zoomBy(1.25);
          }}
          aria-label="Zoom in"
        >
          +
        </button>
      </div>
    </div>
  );
}

function Edge({
  edge,
  placement,
  lit,
  dim,
  dimTo,
}: {
  edge: GraphEdge;
  placement: Placement;
  lit: boolean;
  dim: boolean;

  dimTo: number;
}): JSX.Element | null {
  const from = placement[edge.fromNodeId];
  const to = placement[edge.toNodeId];

  if (from === undefined || to === undefined) {
    return null;
  }

  const x1 = from.x + NODE_WIDTH;
  const y1 = from.y + NODE_HEIGHT / 2;
  const x2 = to.x;
  const y2 = to.y + NODE_HEIGHT / 2;
  const bend = Math.max(48, Math.abs(x2 - x1) * 0.5);

  return (
    <path
      className="fo-graph-edge"
      data-kind={edge.kind}
      data-lit={lit}
      d={`M${String(x1)},${String(y1)} C${String(x1 + bend)},${String(y1)} ${String(x2 - bend)},${String(y2)} ${String(x2)},${String(y2)}`}
      opacity={dim ? dimTo : undefined}
    />
  );
}
