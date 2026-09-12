import {
  Background,
  BackgroundVariant,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  useNodesState,
  useReactFlow,
  useStore,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react';
import { useCallback, useEffect, useMemo, useRef, useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { SupportedLocale } from '../../../i18n';
import { Avatar } from '../../../shared/ui/Avatar';
import { Icon } from '../../../shared/ui/Icon';
import { healthOf, type Band, type BandCard, type CardState } from '../model/bands';
import { findPerson, type Highlight } from '../model/findPerson';
import {
  CARD_WIDTH,
  HEAD_WIDTH,
  planeOf,
  runNodeId,
  type PlacedLane,
  type PlacedRun,
  type PlacedSection,
  type PlaneSectionInput,
} from '../model/plane';
import { anchorTip, type CardTip } from '../model/tip';
import { HEALTH } from '../model/tone';
import { StepCard } from './OperationsBoard';
import { RunHead } from './RunHead';
import { StepCardTip } from './StepCardTip';

import '@xyflow/react/dist/style.css';

interface OperationsPlaneProps {
  sections: readonly PlaneSectionInput[];
  loading: boolean;
  locale: SupportedLocale;

  names: ReadonlyMap<string, string>;
  onAddTask: (instanceId: string) => void;

  onOpenTask: (taskId: string) => void;

  runAction?: (runId: string) => ReactNode;
}

const STATES: readonly CardState[] = ['notStarted', 'inProgress', 'blocked', 'done'];

const STATE_DOT: Record<CardState, string> = {
  notStarted: 'var(--faint)',
  inProgress: 'var(--brand)',
  blocked: 'var(--alert)',
  done: 'var(--done)',
};

const FADED = 0.24;

const EDGE_CORNER = 18;

const RUN_INSET = 24;

type CardNodeData = {
  card: BandCard;
  dimmed: boolean;
  faded: boolean;
  found: boolean;
  onOpenTask: (taskId: string) => void;
  onShowTip: (card: BandCard, element: HTMLElement) => void;
  onHideTip: () => void;
};

type RunNodeData = {
  band: Band;
  locale: SupportedLocale;
  ownerName: string | undefined;
  onAddTask: (instanceId: string) => void;
  action?: ReactNode;
};

type RunBoxData = { run: PlacedRun; faded: boolean; found: boolean };
type LaneNodeData = { lane: PlacedLane; name: string | undefined; faded: boolean };
type SectionNodeData = { section: PlacedSection; onToggle: (key: string) => void };

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
      style={{ opacity: 0, width: 1, height: 1, minWidth: 1, minHeight: 1, border: 'none' }}
    />
  );
}

function CardNode({ data }: NodeProps<Node<CardNodeData>>): JSX.Element {
  return (
    <div
      style={{
        position: 'relative',
        width: CARD_WIDTH,
        opacity: data.faded ? FADED : 1,
        transition: 'opacity 150ms var(--ease)',
      }}
    >
      <Anchor type="target" position={Position.Left} />

      <div
        style={
          data.found
            ? {
                borderRadius: 'var(--radius-control)',
                boxShadow: '0 0 0 2px var(--brand)',
              }
            : undefined
        }
      >
        <StepCard
          card={data.card}
          dimmed={data.dimmed}
          leader={false}
          onOpenTask={data.onOpenTask}
          onShowTip={data.onShowTip}
          onHideTip={data.onHideTip}
        />
      </div>
      <Anchor type="source" position={Position.Right} />
    </div>
  );
}

function RunNode({ data }: NodeProps<Node<RunNodeData>>): JSX.Element {
  return (
    <div style={{ position: 'relative', width: HEAD_WIDTH }}>
      <RunHead
        band={data.band}
        locale={data.locale}
        ownerName={data.ownerName}
        onAddTask={data.onAddTask}
        action={data.action}
      />
      <Anchor type="source" position={Position.Right} />
    </div>
  );
}

function RunBoxNode({ data }: NodeProps<Node<RunBoxData>>): JSX.Element {
  const tone = HEALTH[healthOf(data.run.band.cards)];
  const { box } = data.run;

  return (
    <div
      style={{
        width: box.width,
        height: box.height,
        boxSizing: 'border-box',
        borderRadius: 'var(--radius-node)',
        border: `1px solid ${data.found ? 'var(--brand)' : tone.edge}`,
        background: tone.band,

        opacity: data.faded ? FADED : data.run.band.paused ? 0.58 : 1,
        transition: 'opacity 150ms var(--ease)',
        boxShadow: data.found
          ? '0 0 0 2px color-mix(in srgb, var(--brand) 35%, transparent)'
          : undefined,
      }}
    />
  );
}

function LaneNode({ data }: NodeProps<Node<LaneNodeData>>): JSX.Element | null {
  const { t } = useTranslation();

  if (!data.lane.titled) {
    return null;
  }

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-2)',
        opacity: data.faded ? FADED : 1,
        whiteSpace: 'nowrap',
      }}
    >
      {data.lane.personId === undefined ? null : (
        <Avatar id={data.lane.personId} name={data.name ?? ''} size={18} />
      )}
      <span style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--slate)' }}>
        {data.name ?? t('canvas.board.formerMember')}
      </span>
    </div>
  );
}

function SectionNode({ data }: NodeProps<Node<SectionNodeData>>): JSX.Element {
  const { t } = useTranslation();
  const { section } = data;

  return (
    <div
      style={{
        width: section.width,
        height: section.height,
        borderRadius: '24px',
        border: `1px ${section.named ? 'solid' : 'dashed'} var(--line-soft)`,
        background: section.named
          ? 'color-mix(in srgb, var(--brand-soft) 45%, transparent)'
          : 'var(--surface)',
        padding: section.collapsed ? '0' : '14px 20px',
        boxSizing: 'border-box',
        display: section.collapsed ? 'flex' : 'block',
        alignItems: 'center',
      }}
    >
      <button
        type="button"
        className="nodrag"
        aria-expanded={!section.collapsed}
        onClick={() => data.onToggle(section.key)}
        style={{
          pointerEvents: 'auto',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          border: 'none',
          background: 'transparent',
          cursor: 'pointer',
          padding: section.collapsed ? '0 16px' : '0',
          font: '600 var(--text-sm) var(--font-sans)',
          letterSpacing: '0.04em',
          textTransform: 'uppercase',
          color: section.named ? 'var(--brand-dark)' : 'var(--faint)',
          whiteSpace: 'nowrap',
        }}
      >
        <Icon name={section.collapsed ? 'plus' : 'minus'} size={10} strokeWidth={1.8} />
        {section.title}

        <span style={{ fontWeight: 400, textTransform: 'none', letterSpacing: 0 }}>
          {t('canvas.board.runCount', { count: section.runCount })}
        </span>
      </button>
    </div>
  );
}

const NODE_TYPES = {
  card: CardNode,
  run: RunNode,
  runBox: RunBoxNode,
  lane: LaneNode,
  section: SectionNode,
};

function PlaneControls({ onTidy }: { onTidy: () => void }): JSX.Element {
  const { t } = useTranslation();
  const { zoomIn, zoomOut, fitView } = useReactFlow();
  const zoom = useStore((state) => state.transform[2]);

  return (
    <div className="fo-plane-float fo-plane-float-start" style={{ zIndex: 6 }}>
      <button
        type="button"
        className="fo-plane-control"
        aria-label={t('canvas.board.zoomOut')}
        onClick={() => void zoomOut()}
      >
        <Icon name="minus" size={11} strokeWidth={1.6} />
      </button>

      <span
        style={{
          minWidth: '38px',
          textAlign: 'center',
          fontSize: 'var(--text-xs)',
          fontWeight: 500,
          color: 'var(--slate)',
        }}
      >
        {Math.round(zoom * 100)}%
      </span>
      <button
        type="button"
        className="fo-plane-control"
        aria-label={t('canvas.board.zoomIn')}
        onClick={() => void zoomIn()}
      >
        <Icon name="plus" size={11} strokeWidth={1.6} />
      </button>
      <span aria-hidden="true" className="fo-plane-divider" />
      <button
        type="button"
        className="fo-plane-control"
        onClick={() => void fitView({ padding: 0.12 })}
      >
        <Icon name="fit" size={11} strokeWidth={1.4} />
        {t('canvas.board.fit')}
      </button>

      <button type="button" className="fo-plane-control" onClick={onTidy}>
        <Icon name="reset" size={11} strokeWidth={1.4} />
        {t('canvas.board.tidy')}
      </button>
    </div>
  );
}

export function OperationsPlane({
  sections,
  loading,
  locale,
  names,
  onAddTask,
  onOpenTask,
  runAction,
}: OperationsPlaneProps): JSX.Element {
  const { t } = useTranslation();
  const [visible, setVisible] = useState<ReadonlySet<CardState>>(new Set(STATES));
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(new Set());
  const [looking, setLooking] = useState('');

  const plane = useMemo(() => planeOf(sections, collapsed), [sections, collapsed]);

  const [tip, setTip] = useState<CardTip | null>(null);

  const showTip = useCallback((card: BandCard, element: HTMLElement) => {
    setTip(anchorTip(card, element.getBoundingClientRect()));
  }, []);
  const hideTip = useCallback(() => {
    setTip(null);
  }, []);
  const toggleSection = useCallback((key: string) => {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }, []);

  const highlight: Highlight = useMemo(
    () =>
      findPerson(
        looking,
        sections.flatMap((section) =>
          section.lanes.flatMap((lane) =>
            lane.bands.map((band) => ({
              id: band.id,
              ownerName: names.get(band.ownerId) ?? null,
              cards: band.cards.map((card) => ({ id: card.id, assigneeName: card.assigneeName })),
            })),
          ),
        ),
      ),
    [looking, sections, names],
  );

  const computed = useMemo<Node[]>(() => {
    const faded = (runId: string) => highlight.active && !highlight.runs.has(runId);

    const runBoxes = new Map(plane.runs.map((run) => [run.band.id, run.box]));

    const insideItsRun = (runId: string): [[number, number], [number, number]] | undefined => {
      const box = runBoxes.get(runId);
      if (box === undefined) {
        return undefined;
      }
      return [
        [box.x + RUN_INSET, box.y + RUN_INSET],
        [box.x + box.width - RUN_INSET, box.y + box.height - RUN_INSET],
      ];
    };

    const panelNodes: Node[] = plane.sections.map((section) => ({
      id: `section-${section.key}`,
      type: 'section',
      position: { x: section.x, y: section.y },
      data: { section, onToggle: toggleSection },
      draggable: false,
      selectable: false,
      zIndex: -2,

      style: { pointerEvents: 'none' },
    }));

    const boxes: Node[] = plane.runs.map((run) => ({
      id: `box-${run.band.id}`,
      type: 'runBox',
      position: { x: run.box.x, y: run.box.y },
      data: { run, faded: faded(run.band.id), found: highlight.runs.has(run.band.id) },
      draggable: false,
      selectable: false,
      zIndex: -1,
      style: { pointerEvents: 'none' },
    }));

    const laneTitles: Node[] = plane.lanes
      .filter((lane) => lane.titled)
      .map((lane) => ({
        id: `lane-${lane.key}`,
        type: 'lane',
        position: { x: lane.x, y: lane.y },
        data: {
          lane,
          name: lane.personId === undefined ? undefined : names.get(lane.personId),

          faded:
            highlight.active &&
            lane.personId !== undefined &&
            !(names.get(lane.personId) ?? '')
              .toLocaleLowerCase()
              .includes(looking.trim().toLocaleLowerCase()),
        },
        draggable: false,
        selectable: false,
        style: { pointerEvents: 'none' },
      }));

    const heads: Node[] = plane.runs.map((run) => ({
      id: runNodeId(run.band.id),
      type: 'run',
      position: { x: run.x, y: run.y },
      data: {
        band: run.band,
        locale,
        ownerName: names.get(run.band.ownerId),
        onAddTask,
        action: runAction?.(run.band.id),
      },

      draggable: false,
      connectable: false,
      style: { opacity: faded(run.band.id) ? FADED : 1 },
    }));

    const steps: Node[] = plane.cards.map((placed) => ({
      id: placed.card.id,
      type: 'card',
      position: { x: placed.x, y: placed.y },
      data: {
        card: placed.card,
        dimmed: !visible.has(placed.card.state),
        faded: faded(placed.runId) && !highlight.cards.has(placed.card.id),
        found: highlight.cards.has(placed.card.id),
        onOpenTask,
        onShowTip: showTip,
        onHideTip: hideTip,
      },
      draggable: true,
      connectable: false,
      extent: insideItsRun(placed.runId),
    }));

    return [...panelNodes, ...boxes, ...laneTitles, ...heads, ...steps];
  }, [
    plane,
    locale,
    names,
    onAddTask,
    onOpenTask,
    runAction,
    visible,
    showTip,
    hideTip,
    toggleSection,
    highlight,
    looking,
  ]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(computed);

  const signature = useMemo(
    () =>
      [
        plane.sections.map((section) => `${section.key}:${String(section.collapsed)}`).join(','),
        plane.lanes.map((lane) => lane.key).join(','),
        plane.runs.map((run) => run.band.id).join(','),
        plane.cards.map((placed) => placed.card.id).join(','),
        plane.links.map((link) => link.id).join(','),
      ].join('|'),
    [plane],
  );
  const laidOutFor = useRef(signature);

  useEffect(() => {
    if (laidOutFor.current !== signature) {
      laidOutFor.current = signature;
      setNodes(computed);
      return;
    }
    setNodes((current: Node[]) => {
      const moved = new Map(current.map((node) => [node.id, node.position]));
      return computed.map((node) => ({ ...node, position: moved.get(node.id) ?? node.position }));
    });
  }, [computed, signature, setNodes]);

  const tidy = useCallback(() => {
    setNodes(computed);
  }, [computed, setNodes]);

  const edges = useMemo<Edge[]>(
    () =>
      plane.links.map((link) => {
        if (link.kind === 'MEMBERSHIP') {
          return {
            id: link.id,
            source: link.from,
            target: link.to,
            type: 'smoothstep',

            pathOptions: { borderRadius: EDGE_CORNER },
            style: { stroke: 'var(--line)', strokeWidth: 1, strokeDasharray: '2 4', opacity: 0.7 },
            focusable: false,
            reconnectable: false,
          };
        }
        return {
          id: link.id,
          source: link.from,
          target: link.to,
          type: 'smoothstep',
          pathOptions: { borderRadius: EDGE_CORNER },

          style: {
            stroke: link.met ? 'var(--line-strong)' : 'var(--line)',
            strokeWidth: 1.5,
            strokeDasharray: link.met ? undefined : '4 4',
          },
          markerEnd: link.met ? 'operations-dot-met' : 'operations-dot-unmet',
          focusable: false,
          reconnectable: false,
        };
      }),
    [plane],
  );

  function toggle(state: CardState): void {
    const next = new Set(visible);
    if (next.has(state)) {
      next.delete(state);
    } else {
      next.add(state);
    }

    setVisible(next.size === 0 ? new Set(STATES) : next);
  }

  if (loading) {
    return (
      <p style={{ margin: 0, padding: 'var(--space-6)', color: 'var(--muted)' }}>
        {t('canvas.board.loading')}
      </p>
    );
  }

  if (plane.sections.length === 0) {
    return (
      <p style={{ margin: 0, padding: 'var(--space-6)', color: 'var(--muted)' }}>
        {t('canvas.board.empty')}
      </p>
    );
  }

  const running = sections
    .flatMap((section) => section.lanes.flatMap((lane) => lane.bands))
    .filter((band) => band.running).length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 'var(--space-3)',
          padding: 'var(--space-2) var(--space-6)',
          borderBlockEnd: '1px solid var(--line)',
          background: 'var(--surface)',
        }}
      >
        <span style={{ fontSize: 'var(--text-sm)', color: 'var(--faint)' }}>
          {t('canvas.board.summary', { count: running })}
        </span>

        <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <span className="fo-visually-hidden">{t('canvas.board.findPerson')}</span>
          <input
            type="search"
            className="ui-control"
            placeholder={t('canvas.board.findPerson')}
            value={looking}
            onChange={(event) => setLooking(event.target.value)}
            style={{ width: '190px', fontSize: 'var(--text-xs)', padding: '3px var(--space-2)' }}
          />
        </label>
        {highlight.active ? (
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--brand-dark)' }}>
            {highlight.runs.size === 0
              ? t('canvas.board.foundNobody')
              : t('canvas.board.showingTheirWork')}
          </span>
        ) : null}

        <div style={{ marginInlineStart: 'auto', display: 'flex', gap: 'var(--space-1)' }}>
          {STATES.map((state) => (
            <button
              key={state}
              type="button"
              aria-pressed={visible.has(state)}
              onClick={() => toggle(state)}
              className="fo-legend-chip"
              style={{ opacity: visible.has(state) ? 1 : 0.45 }}
            >
              <span
                aria-hidden="true"
                style={{
                  width: '7px',
                  height: '7px',
                  borderRadius: '50%',
                  background: STATE_DOT[state],
                }}
              />
              {t(`canvas.board.state.${state}`)}
            </button>
          ))}
        </div>
      </div>

      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={NODE_TYPES}
          onNodesChange={onNodesChange}
          nodesConnectable={false}
          nodesDraggable

          nodeDragThreshold={3}
          edgesFocusable={false}
          edgesReconnectable={false}
          elementsSelectable
          fitView

          fitViewOptions={{ padding: 0.12, minZoom: 0.45 }}
          minZoom={0.1}
          maxZoom={1.6}
          proOptions={{ hideAttribution: false }}
        >
          <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true">
            <defs>
              <marker
                id="operations-dot-met"
                viewBox="0 0 8 8"
                markerWidth="8"
                markerHeight="8"
                refX="4"
                refY="4"
              >
                <circle cx="4" cy="4" r="3" fill="var(--line-strong)" />
              </marker>
              <marker
                id="operations-dot-unmet"
                viewBox="0 0 8 8"
                markerWidth="8"
                markerHeight="8"
                refX="4"
                refY="4"
              >
                <circle cx="4" cy="4" r="3" fill="var(--line)" />
              </marker>
            </defs>
          </svg>

          <Background
            id="lattice"
            variant={BackgroundVariant.Lines}
            gap={110}
            lineWidth={1}
            color="var(--line-soft)"
          />
          <Background
            id="grain"
            variant={BackgroundVariant.Dots}
            gap={22}
            size={1}
            color="var(--line)"
          />

          <MiniMap
            ariaLabel={t('canvas.board.overview')}
            pannable
            zoomable
            nodeStrokeWidth={2}
            nodeColor={(node) =>
              node.type === 'card'
                ? STATE_DOT[(node.data as CardNodeData).card.state]
                : node.type === 'run'
                  ? 'var(--brand-dark)'
                  : 'transparent'
            }
          />
          <PlaneControls onTidy={tidy} />
        </ReactFlow>

        <StepCardTip tip={tip} />
      </div>
    </div>
  );
}
