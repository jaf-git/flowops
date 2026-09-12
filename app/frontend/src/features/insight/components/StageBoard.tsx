import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import {
  STAGES,
  describeStage,
  shapeInWords,
  type Finding,
  type Recommendation,
  type Stage,
  type WrittenDown,
} from '../api/analysisApi';
import { useDismissProposal, useWriteItDown } from '../hooks/useAnalysis';

interface StageBoardProps {
  readonly findings: readonly Finding[];

  readonly reached: Stage | undefined;
  readonly selected: string | undefined;
  readonly onSelect: (findingId: string) => void;

  readonly recommendations: readonly Recommendation[];
}

export function StageBoard({
  findings,
  reached,
  selected,
  onSelect,
  recommendations,
}: StageBoardProps): JSX.Element {
  const { t } = useTranslation();

  const reachedIndex = reached === undefined ? -1 : STAGES.indexOf(reached);

  return (
    <div className="fo-pipe-board">
      {STAGES.map((stage, index) => {
        const inStage = findings.filter((finding) => finding.stage === stage);
        const described = describeStage(stage);
        const ran = reachedIndex >= index;

        return (
          <section key={stage} className="fo-pipe-track" data-stage={stage} data-ran={ran}>
            <header className="fo-pipe-track-head">
              <span aria-hidden="true" className="fo-pipe-tab" data-stage={stage} />

              <span className="fo-pipe-track-name">{described.name}</span>

              {(stage === 'RECOMMEND' ? recommendations.length : inStage.length) === 0 ? null : (
                <span className="fo-pipe-track-count">
                  {stage === 'RECOMMEND' ? recommendations.length : inStage.length}
                </span>
              )}
            </header>

            <p className="fo-pipe-track-asks">{described.asks}</p>

            <div className="fo-pipe-track-body">
              {stage === 'RECOMMEND' && ran ? (
                recommendations.length === 0 ? (
                  <p className="fo-pipe-track-empty">{t('pipeline.stage.nothing')}</p>
                ) : (
                  recommendations.map((proposal) => (
                    <ProposalCard
                      key={proposal.id}
                      proposal={proposal}

                      shape={
                        findings
                          .filter((finding) => finding.subjectKind === 'SHAPE')
                          .find((finding) => finding.id === proposal.findingId)?.subjectKey
                      }
                    />
                  ))
                )
              ) : !ran ? (
                <p className="fo-pipe-track-empty">{t('pipeline.stage.notRun')}</p>
              ) : inStage.length === 0 ? (
                <p className="fo-pipe-track-empty">{t('pipeline.stage.nothing')}</p>
              ) : (
                inStage.map((finding) => (
                  <FindingCard
                    key={finding.id}
                    finding={finding}
                    selected={selected === finding.id}
                    onSelect={onSelect}
                  />
                ))
              )}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function FindingCard({
  finding,
  selected,
  onSelect,
}: {
  finding: Finding;
  selected: boolean;
  onSelect: (id: string) => void;
}): JSX.Element {
  const { t } = useTranslation();

  return (
    <button
      type="button"
      className="fo-pipe-card"
      aria-pressed={selected}
      onClick={() => {
        onSelect(finding.id);
      }}
    >
      <span className="fo-pipe-card-eyebrow">{shapeInWords(finding.subjectKey)}</span>

      <span className="fo-pipe-card-title">{finding.phrasing ?? finding.headline}</span>

      <span className="fo-pipe-card-meta">
        <span className="fo-pipe-card-evidence">
          {t('pipeline.card.evidence', { count: finding.sampleSize })}
        </span>

        {finding.measure === null || finding.unit === null || finding.unit === 'cases' ? null : (
          <span className="fo-pipe-card-measure">
            {finding.unit.startsWith('%')
              ? `${String(finding.measure)}${finding.unit}`
              : `${String(finding.measure)} ${finding.unit}`}
          </span>
        )}
      </span>
    </button>
  );
}

function ProposalCard({
  proposal,
  shape,
}: {
  proposal: Recommendation;

  shape: string | undefined;
}): JSX.Element {
  const { t } = useTranslation();

  const write = useWriteItDown();
  const putAside = useDismissProposal();

  const [naming, setNaming] = useState(false);
  const [name, setName] = useState('');
  const [written, setWritten] = useState<WrittenDown | undefined>(undefined);

  const canWrite = proposal.kind === 'WRITE_IT_DOWN' && shape !== undefined;

  if (written !== undefined) {
    return (
      <article className="fo-pipe-proposal" data-written="true">
        <span className="fo-pipe-card-eyebrow">{t('pipeline.proposal.written')}</span>
        <span className="fo-pipe-proposal-title">{written.name}</span>
        <span className="fo-pipe-proposal-detail">
          {t('pipeline.proposal.writtenDetail', { steps: written.steps.join(' → ') })}
        </span>
        <span className="fo-pipe-card-meta">{t('pipeline.proposal.whereItIs')}</span>
      </article>
    );
  }

  return (
    <article className="fo-pipe-proposal" data-confidence={proposal.confidence}>
      <span className="fo-pipe-card-eyebrow">
        {t(`pipeline.confidence.${proposal.confidence}`)}
      </span>

      <span className="fo-pipe-proposal-title">{proposal.headline}</span>

      {proposal.detail === null ? null : (
        <span className="fo-pipe-proposal-detail">{proposal.detail}</span>
      )}

      <span className="fo-pipe-card-meta">
        {t('pipeline.card.evidence', { count: proposal.sampleSize })}
      </span>

      {naming ? (
        <form
          className="fo-pipe-proposal-naming"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim() === '') {
              return;
            }
            write.mutate(
              { recommendationId: proposal.id, name: name.trim() },
              { onSuccess: setWritten },
            );
          }}
        >
          <label className="fo-pipe-proposal-label" htmlFor={`name-${proposal.id}`}>
            {t('pipeline.proposal.nameIt')}
          </label>

          <input
            id={`name-${proposal.id}`}
            className="ui-control"
            value={name}

            autoFocus
            onChange={(event) => {
              setName(event.target.value);
            }}
          />

          <div className="fo-pipe-proposal-actions">
            <button
              type="submit"
              className="ui-button ui-button-primary"
              disabled={name.trim() === '' || write.isPending}
            >
              {write.isPending ? t('pipeline.proposal.writing') : t('pipeline.proposal.confirm')}
            </button>

            <button
              type="button"
              className="ui-button ui-button-quiet"
              onClick={() => {
                setNaming(false);
              }}
            >
              {t('pipeline.proposal.cancel')}
            </button>
          </div>

          {write.isError ? <p className="fo-pipe-proposal-refusal">{write.error.message}</p> : null}
        </form>
      ) : (
        <div className="fo-pipe-proposal-actions">
          {canWrite ? (
            <button
              type="button"
              className="ui-button ui-button-primary"
              onClick={() => {
                setName(shapeInWords(shape));
                setNaming(true);
              }}
            >
              {t('pipeline.proposal.writeItDown')}
            </button>
          ) : null}

          <button
            type="button"
            className="ui-button ui-button-quiet"
            disabled={putAside.isPending}
            onClick={() => {
              putAside.mutate(proposal.id);
            }}
          >
            {t('pipeline.proposal.notNow')}
          </button>
        </div>
      )}
    </article>
  );
}
