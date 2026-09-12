import type { JSX } from 'react';

import type { GraphNode as GraphNodeData } from '../api/jobGraphApi';
import { appearanceOf, elapsedWithPhase, performerOf } from '../model/nodeAppearance';
import type { Point } from '../model/graphLayout';

interface GraphNodeProps {
  readonly node: GraphNodeData;
  readonly at: Point;
  readonly selected: boolean;

  readonly dim: number;
  readonly onSelect: (nodeId: string) => void;
}

export function GraphNode({ node, at, selected, dim, onSelect }: GraphNodeProps): JSX.Element {
  const seen = appearanceOf(node);
  const who = performerOf(node);

  return (
    <button
      type="button"
      className="fo-gnode"
      data-state={seen.state}
      data-selected={selected}

      data-node-id={node.nodeId}

      style={{ transform: `translate(${String(at.x)}px, ${String(at.y)}px)`, opacity: dim }}

      aria-pressed={selected}
      aria-label={`${node.workType} · ${who} · ${seen.label} · ${elapsedWithPhase(node)}`}
      onClick={() => {
        onSelect(node.nodeId);
      }}
    >
      <span className="fo-gnode-head">
        <span className="fo-gnode-status" aria-hidden="true">
          {seen.glyph}
        </span>

        <span className="fo-gnode-state">{seen.label}</span>

        <span className="fo-gnode-kind" aria-hidden="true" data-type={node.workType} />
      </span>

      <span className="fo-gnode-title">{node.title ?? node.text ?? node.workType}</span>

      {node.detail === null ? null : <span className="fo-gnode-detail">{node.detail}</span>}

      <span className="fo-gnode-facts">
        <Fact label="who" value={node.unclaimed ? 'Nobody yet' : node.performerName} />
        <Fact label="marked" value={node.markerName} />
        <Fact label="client" value={node.client} />
        <Fact label="project" value={node.projectLabel} />
        <Fact label="dept" value={node.department} />
        <Fact label="type" value={node.workType} />
        <Fact label="activity" value={node.activity} />
      </span>

      <span className="fo-gnode-meta">
        <span className="fo-gnode-elapsed">{elapsedWithPhase(node)}</span>

        {node.checklist === null || node.checklist.length === 0 ? null : (
          <span className="fo-gnode-steps">{`${String(node.checklist.length)} steps`}</span>
        )}
      </span>
    </button>
  );
}

function Fact({ label, value }: { label: string; value: string | null }): JSX.Element | null {
  if (value === null || value.trim() === '') {
    return null;
  }

  return (
    <span className="fo-gnode-fact">
      <span className="fo-gnode-fact-label">{label}</span>
      <span className="fo-gnode-fact-value">{value}</span>
    </span>
  );
}
