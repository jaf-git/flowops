import { useMemo, type JSX } from 'react';

import type { GraphEdge, GraphNode } from '../api/jobGraphApi';
import { journeyOf, lineageOf, type JourneyStep } from '../model/journey';
import { appearanceOf, elapsedWithPhase, performerOf } from '../model/nodeAppearance';

interface JobJourneyProps {
  readonly nodes: readonly GraphNode[];
  readonly edges: readonly GraphEdge[];
  readonly selected: string | null;
  readonly onSelect: (nodeId: string) => void;

  readonly onOpenMessage?: (conversationId: string, messageId: string) => void;
}

export function JobJourney({
  nodes,
  edges,
  selected,
  onSelect,
  onOpenMessage,
}: JobJourneyProps): JSX.Element {
  const steps = useMemo(() => journeyOf(nodes, edges), [nodes, edges]);
  const line = useMemo(
    () => (selected === null ? null : lineageOf(selected, edges)),
    [selected, edges],
  );

  return (
    <ol className="fo-journey" aria-label="What happened, in order">
      {steps.map((step) => (
        <JourneyRow
          key={step.node.nodeId}
          step={step}
          selected={step.node.nodeId === selected}

          aside={line !== null && !line.has(step.node.nodeId)}
          onSelect={onSelect}
          onOpenMessage={onOpenMessage}
        />
      ))}
    </ol>
  );
}

function JourneyRow({
  step,
  selected,
  aside,
  onSelect,
  onOpenMessage,
}: {
  step: JourneyStep;
  selected: boolean;
  aside: boolean;
  onSelect: (nodeId: string) => void;
  onOpenMessage?: (conversationId: string, messageId: string) => void;
}): JSX.Element {
  const { node } = step;
  const seen = appearanceOf(node);
  const who = performerOf(node);
  const elapsed = elapsedWithPhase(node);

  return (
    <li className="fo-journey-step" data-aside={aside}>
      {step.threadChanged ? (
        <p className="fo-journey-handover">Picked up in another conversation</p>
      ) : null}

      <div className="fo-journey-row">
        <div className="fo-journey-gutter">
          <span className="fo-journey-disc" data-state={seen.state} aria-hidden="true">
            {seen.glyph}
          </span>
          <span className="fo-journey-index">{String(step.index).padStart(2, '0')}</span>
        </div>

        <button
          type="button"
          className="fo-journey-card"
          data-selected={selected}
          aria-pressed={selected}
          aria-label={`Step ${String(step.index)}: ${node.workType} · ${who} · ${seen.label} · ${elapsed}`}
          onClick={() => {
            onSelect(node.nodeId);
          }}
        >
          <span className="fo-journey-head">
            <span className="fo-journey-title">{node.workType}</span>

            <span className="fo-journey-status" data-state={seen.state}>
              {seen.label}
            </span>
          </span>

          <span className="fo-journey-meta">
            <span className="fo-journey-who">{who}</span>

            <span className="fo-journey-elapsed">{elapsed}</span>
          </span>

          <span className="fo-journey-detail">
            {node.boundary ? (
              <span className="fo-journey-fact">The engagement itself — never joinable</span>
            ) : null}
            {node.unclaimed ? (
              <span className="fo-journey-fact">Nobody has taken this on yet</span>
            ) : null}
            {node.closeKind === null ? (
              <span className="fo-journey-fact">Still open</span>
            ) : (
              <span className="fo-journey-fact">{`Ended: ${seen.label}`}</span>
            )}
          </span>
        </button>
      </div>

      {onOpenMessage !== undefined && node.messageId !== null && node.conversationId !== null ? (
        <button
          type="button"
          className="ui-button ui-button-quiet fo-journey-open"
          onClick={() => {
            onOpenMessage(node.conversationId as string, node.messageId as string);
          }}
        >
          Open what was said
        </button>
      ) : null}
    </li>
  );
}
