import { useQuery } from '@tanstack/react-query';
import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import {
  fetchDecisionSources,
  fetchPipelineDecisions,
  type PipelineDecision,
} from '../api/pipelineDecisionsApi';
import { stepName } from '../stepName';

export function DiscoveredProcesses({
  onOpenGraph,
}: {
  onOpenGraph?: (conversationId: string) => void;
}): JSX.Element | null {
  const { t } = useTranslation();
  const [open, setOpen] = useState<string | undefined>(undefined);

  const processes = useQuery({
    queryKey: ['node-pipeline', 'decisions', 'DRAFT_PROCESS'],
    queryFn: () => fetchPipelineDecisions('DRAFT_PROCESS'),
  });

  const kinds = useQuery({
    queryKey: ['node-pipeline', 'decisions', 'STEP_KIND'],
    queryFn: () => fetchPipelineDecisions('STEP_KIND', 100),
  });

  if (!processes.data?.length) {
    return null;
  }

  return (
    <section className="fo-pb-panel">
      <div className="fo-pb-between">
        <div className="fo-pb-stack fo-pb-stack--tight">
          <span className="fo-pb-eyebrow">{t('board.whatRepeats')}</span>
          <h3 className="fo-pb-title">{t('board.nShapes', { count: processes.data.length })}</h3>
        </div>
      </div>

      {processes.data.map((process) => (
        <Shape
          key={process.id}
          process={process}
          kinds={kinds.data ?? []}
          open={open === process.id}
          onToggle={() => setOpen((was) => (was === process.id ? undefined : process.id))}
          onOpenGraph={onOpenGraph}
        />
      ))}

      <p className="fo-pb-note">{t('board.shapesNote')}</p>
    </section>
  );
}

function Step({
  kind,
  onOpenGraph,
}: {
  kind: PipelineDecision;
  onOpenGraph?: (conversationId: string) => void;
}): JSX.Element {
  const [open, setOpen] = useState(false);

  const marks = useQuery({
    queryKey: ['node-pipeline', 'decisions', kind.id, 'sources'],
    queryFn: () => fetchDecisionSources(kind.id),
    enabled: open,
  });

  const nodes = (marks.data ?? []).filter((source) => source.kind === 'NODE');

  return (
    <div className="fo-pb-stack fo-pb-stack--tight">
      <button
        type="button"
        className="fo-fw-evidence fo-pb-shape"
        aria-expanded={open}
        onClick={() => setOpen((was) => !was)}
      >
        <span className="fo-fw-evidence-id">{stepName(kind.subject)}</span>
        <span className="fo-pb-mono fo-pb-faint-ink">{kind.reason}</span>
      </button>

      {open
        ? nodes.map((node) =>
            node.conversationId && onOpenGraph ? (
              <button
                type="button"
                className="fo-fw-evidence"
                key={node.id}
                onClick={() => onOpenGraph(node.conversationId as string)}
              >
                <span className="fo-fw-evidence-id">{node.label}</span>
                <span className="fo-pb-mono fo-pb-faint-ink">{node.detail}</span>
              </button>
            ) : (
              <div className="fo-fw-evidence" key={node.id}>
                <span className="fo-fw-evidence-id">{node.label}</span>
                <span className="fo-pb-mono fo-pb-faint-ink">{node.detail}</span>
              </div>
            ),
          )
        : null}
    </div>
  );
}

function Shape({
  process,
  kinds,
  open,
  onToggle,
  onOpenGraph,
}: {
  process: PipelineDecision;

  kinds: readonly PipelineDecision[];
  open: boolean;
  onToggle: () => void;
  onOpenGraph?: (conversationId: string) => void;
}): JSX.Element {
  const { t } = useTranslation();

  const sources = useQuery({
    queryKey: ['node-pipeline', 'decisions', process.id, 'sources'],
    queryFn: () => fetchDecisionSources(process.id),

    enabled: open,
  });

  const stepKeys = process.subject.split(' -> ');
  const steps = stepKeys.map(stepName);

  const mine = stepKeys
    .map((key) => kinds.find((k) => k.subject === key))
    .filter(Boolean) as PipelineDecision[];

  return (
    <div className="fo-pb-stack fo-pb-stack--tight">
      <button
        type="button"
        className="fo-pb-decision fo-pb-shape"
        aria-expanded={open}
        onClick={onToggle}
      >
        <span className="fo-pb-stack fo-pb-stack--tight">
          <span className="fo-fw-action-title">{steps.join(' → ')}</span>

          {process.reason ? (
            <span className="fo-pb-mono fo-pb-faint-ink">{process.reason}</span>
          ) : null}
        </span>
        <span aria-hidden="true">{open ? '−' : '+'}</span>
      </button>

      {open ? (
        <div className="fo-pb-stack fo-pb-stack--tight">
          {sources.isPending ? <p className="fo-fw-foot">{t('board.sourcesLoading')}</p> : null}
          {sources.isError ? <p className="fo-fw-foot">{t('board.sourcesFailed')}</p> : null}

          {sources.data?.length === 0 ? (
            <p className="fo-fw-foot">{t('board.sourcesNone')}</p>
          ) : null}

          {mine.length > 0 ? (
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">{t('board.madeOf')}</span>
              {mine.map((kind) => (
                <Step key={kind.id} kind={kind} onOpenGraph={onOpenGraph} />
              ))}
            </div>
          ) : null}

          {sources.data?.map((source) =>
            source.conversationId && onOpenGraph ? (
              <button
                type="button"
                className="fo-fw-evidence"
                key={source.id}
                onClick={() => onOpenGraph(source.conversationId as string)}
              >
                <span className="fo-fw-evidence-id">{source.label}</span>
                <span className="fo-pb-mono fo-pb-faint-ink">{source.detail}</span>
              </button>
            ) : (
              <div className="fo-fw-evidence" key={source.id}>
                <span className="fo-fw-evidence-id">{source.label}</span>
                <span className="fo-pb-mono fo-pb-faint-ink">{source.detail}</span>
              </div>
            ),
          )}
        </div>
      ) : null}
    </div>
  );
}
