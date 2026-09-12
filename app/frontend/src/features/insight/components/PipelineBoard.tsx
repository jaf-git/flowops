import { useMemo, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { STAGES, type Stage } from '../api/analysisApi';
import { RunEverything } from './RunEverything';
import { PipelineCompositionControl } from './PipelineCompositionControl';
import { DiscoveredProcesses } from './DiscoveredProcesses';
import { StageDetail } from './StageDetail';
import { somethingHappenedAt } from '../stageActivity';
import type { AnalyserLine } from '../api/analyserApi';
import {
  useFindingQueue,
  useLatestAnalysis,
  useLatestNodeRun,
  useRunAnalysers,
  useRunHistory,
  useStuck,
} from '../hooks/useAnalysis';
import '../pipeline-board.css';
import { CountUp } from '../../../shared/motion/CountUp';

export function PipelineBoard(): JSX.Element {
  const { t } = useTranslation();

  const analysis = useLatestAnalysis();
  const nodeRun = useLatestNodeRun();
  const queue = useFindingQueue();
  const stuck = useStuck();
  const history = useRunHistory();
  const runAnalysers = useRunAnalysers();

  const [passesOpen, setPassesOpen] = useState(false);
  const [chosen, setChosen] = useState<string | undefined>(undefined);
  const [openStage, setOpenStage] = useState<Stage | undefined>(undefined);

  const analysers = useMemo(() => analysis.data?.analysers ?? [], [analysis.data]);
  const run = nodeRun.data?.run;
  const rows = (queue.data?.groups ?? []).flatMap((group) => group.items);
  const groups = stuck.data ?? [];

  const health = useMemo(() => tally(analysers), [analysers]);
  const selected = analysers.find((line) => line.id === chosen);

  const byStage = useMemo(() => {
    const counts = new Map<string, number>();
    for (const finding of rows) {
      counts.set(finding.stage, (counts.get(finding.stage) ?? 0) + 1);
    }

    const known = STAGES.filter((stage) => counts.has(stage)).map(
      (stage) => [stage, counts.get(stage) ?? 0] as const,
    );
    const rest = [...counts.entries()].filter(([stage]) => !STAGES.includes(stage as Stage));
    return [...known, ...rest];
  }, [rows]);

  const reached = nodeRun.data?.run.reachedStage;
  const mostForOne = Math.max(1, ...byStage.map(([, n]) => n));

  const findingsPerStage = useMemo(() => new Map(byStage), [byStage]);
  const readTotal = run?.nodesRead ?? 0;
  const inWindow = run?.nodesInWindow ?? readTotal;

  const coverage = useMemo(() => {
    const effort = analysers.find((line) => line.id === 'S3_EFFORT');
    return {
      effortBasis: effort?.itemsRead ?? null,
      read: readTotal,
      unread: Math.max(0, inWindow - readTotal),
      inWindow: Math.max(1, inWindow),
    };
  }, [analysers, readTotal, inWindow]);

  return (
    <div className="fo-pb">
      <div className="fo-pb-main">
        <div className="fo-pb-row1">
          <section className="fo-pb-hero">
            <div className="fo-pb-between">
              <span className="fo-pb-eyebrow">{t('board.lastRun')}</span>

              <span className="fo-pb-mono fo-pb-faint" title={t('board.signatureMeaning')}>
                {run?.signature.slice(0, 6) ?? '—'}
              </span>
            </div>

            <div className="fo-pb-figure-row">
              <span className="fo-pb-hero-figure">
                <CountUp value={readTotal} />
              </span>
              <span className="fo-pb-eyebrow">{t('board.piecesRead')}</span>
            </div>

            <div className="fo-pb-chips">
              <span className="fo-pb-hero-chip">
                {t('board.nFindings', { count: analysis.data?.findingsTotal ?? 0 })}
              </span>
              <span className="fo-pb-hero-chip">
                {t('board.nJobs', { count: run?.jobsRead ?? 0 })}
              </span>
              {health.blocked > 0 ? (
                <span className="fo-pb-hero-chip fo-pb-hero-chip--blocked">
                  {t('board.nBlocked', { count: health.blocked })}
                </span>
              ) : null}
            </div>

            <p className="fo-pb-hero-lede">{t('board.threePasses')}</p>
            <div className="fo-pb-grow" />

            <div className="fo-pb-stack">
              <RunEverything />

              <button
                type="button"
                className="fo-pb-hero-secondary"
                aria-expanded={passesOpen}
                onClick={() => {
                  setPassesOpen((was) => !was);
                }}
              >
                {t('board.runOnePass')} <span aria-hidden="true">{passesOpen ? '▾' : '▸'}</span>
              </button>

              {passesOpen ? (
                <div className="fo-pb-stack fo-pb-stack--tight">
                  <div className="fo-pb-hero-pass">
                    <span>{t('board.pass.nodePipeline')}</span>
                    <span className="fo-pb-mono fo-pb-faint">
                      {t('board.nInWindow', { count: inWindow })}
                    </span>
                  </div>
                  <div className="fo-pb-hero-pass">
                    <span>{t('board.pass.discovery')}</span>
                    <span className="fo-pb-mono fo-pb-faint">{t('board.pass.readsTheGraph')}</span>
                  </div>
                  <div className="fo-pb-hero-pass">
                    <span>{t('board.pass.analysers')}</span>
                    <span className="fo-pb-mono fo-pb-faint">
                      {t('board.pass.overOneSnapshot', { count: analysers.length })}
                    </span>
                  </div>
                </div>
              ) : null}
            </div>
          </section>

          <section className="fo-pb-card">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">{t('board.whatFiguresRestOn')}</span>
              <h3 className="fo-pb-title-sm">{t('board.coverage')}</h3>
            </div>

            <div className="fo-pb-figure-row">
              <span className="fo-pb-figure">{coverage.read}</span>
              <span className="fo-pb-eyebrow">{t('board.marksRead')}</span>
            </div>

            <div
              className="fo-pb-bar"
              role="img"
              aria-label={t('board.coverageAlt', {
                read: coverage.read,
                total: coverage.inWindow,
              })}
            >
              <span
                style={{
                  width: share(coverage.read, coverage.inWindow),
                  background: 'var(--ink-900)',
                  color: 'var(--on-ink)',
                }}
              >
                {coverage.read}
              </span>
              {coverage.unread > 0 ? (
                <span
                  style={{
                    width: share(coverage.unread, coverage.inWindow),
                    background: 'var(--line)',
                  }}
                />
              ) : null}
            </div>

            <div className="fo-pb-stack fo-pb-stack--tight">
              <p className="fo-pb-legend">
                <span className="fo-pb-swatch" style={{ background: 'var(--ink-900)' }} />
                <span className="fo-pb-legend-label">{t('board.readInWindow')}</span>
                <span className="fo-pb-legend-n">{coverage.read}</span>
              </p>
              <p className="fo-pb-legend">
                <span className="fo-pb-swatch" style={{ background: 'var(--line)' }} />
                <span className="fo-pb-legend-label">{t('board.inWindowNotRead')}</span>
                <span className="fo-pb-legend-n">{coverage.unread}</span>
              </p>
            </div>

            <p className="fo-pb-note">
              {coverage.effortBasis === null
                ? t('board.noEffortBasis')
                : t('board.effortBasis', { pieces: coverage.effortBasis, read: coverage.read })}
            </p>
          </section>

          <section className="fo-pb-card">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">{t('board.widthIsCount')}</span>
              <h3 className="fo-pb-title-sm">{t('board.findingsByStage')}</h3>
            </div>

            <div className="fo-pb-stack">
              {byStage.length === 0 ? (
                <p className="fo-pb-quiet">{t('board.noFindingsYet')}</p>
              ) : (
                byStage.map(([stage, count], index) => (
                  <div className="fo-pb-stagebar" key={stage}>
                    <span className="fo-pb-stagebar-name" title={stage}>
                      {stage}
                    </span>
                    <span className="fo-pb-stagebar-track">
                      <span
                        className="fo-pb-stagebar-fill"
                        style={{ width: share(count, mostForOne), background: seriesColour(index) }}
                      />
                    </span>
                    <span className="fo-pb-stagebar-n">{count}</span>
                  </div>
                ))
              )}
            </div>

            <p className="fo-pb-note">{t('board.detectorNote')}</p>
          </section>
        </div>

        {health.blocked > 0 ? (
          <section className="fo-pb-gate">
            <div className="fo-pb-stack">
              <span className="fo-pb-gate-eyebrow">{t('board.gateEyebrow')}</span>
              <h3 className="fo-pb-gate-headline">
                {t('board.gateHeadline', { count: health.blocked })}
              </h3>
              <div className="fo-pb-chips">
                {health.remedies.slice(0, 3).map((remedy) => (
                  <span className="fo-pb-gate-action" key={remedy.text}>
                    {remedy.text}{' '}
                    <span className="fo-pb-gate-action-n">
                      {t('board.unblocks', { count: remedy.n })}
                    </span>
                  </span>
                ))}
              </div>
            </div>

            <div className="fo-pb-gate-aside">
              <div className="fo-pb-donut">
                <span className="fo-pb-donut-ring" style={{ background: donut(health) }} />
                <span className="fo-pb-donut-centre">
                  <span className="fo-pb-donut-n">
                    {health.working}/{health.total}
                  </span>
                  <span className="fo-pb-donut-label">{t('board.ready')}</span>
                </span>
              </div>

              <div className="fo-pb-stack fo-pb-stack--tight fo-pb-grow">
                <p className="fo-pb-legend">
                  <span className="fo-pb-swatch" style={{ background: 'var(--ink-900)' }} />
                  <span className="fo-pb-legend-label">{t('board.working')}</span>
                  <span className="fo-pb-legend-n">{health.working}</span>
                </p>
                <p className="fo-pb-legend">
                  <span className="fo-pb-swatch" style={{ background: 'var(--on-warning)' }} />
                  <span className="fo-pb-legend-label">{t('board.blocked')}</span>
                  <span className="fo-pb-legend-n">{health.blocked}</span>
                </p>
                <p className="fo-pb-legend">
                  <span className="fo-pb-swatch" style={{ background: 'rgb(11 11 12 / 22%)' }} />
                  <span className="fo-pb-legend-label">{t('board.ranSaidNothing')}</span>
                  <span className="fo-pb-legend-n">{health.quiet}</span>
                </p>
              </div>
            </div>
          </section>
        ) : null}

        <section className="fo-pb-card">
          <div className="fo-pb-stack fo-pb-stack--tight">
            <span className="fo-pb-eyebrow">{t('board.eachStageReads')}</span>
            <h3 className="fo-pb-title">{t('board.theSixStages')}</h3>
          </div>

          <div className="fo-pb-track">
            {STAGES.map((stage, index) => (
              <div className="fo-pb-track-cell" key={stage}>
                <button
                  type="button"
                  className={stage === openStage ? 'fo-pb-stage fo-pb-stage--on' : 'fo-pb-stage'}
                  aria-expanded={stage === openStage}
                  onClick={() => {
                    setOpenStage((was) => (was === stage ? undefined : stage));
                  }}
                >
                  <div className="fo-pb-between">
                    <span
                      className="fo-pb-tile"
                      style={{
                        background: 'var(--surface)',
                        color: somethingHappenedAt(stage, reached, findingsPerStage.get(stage) ?? 0)
                          ? stageColour(stage)
                          : 'var(--line-strong)',
                      }}
                    >
                      {stageGlyph(stage)}
                    </span>
                    {findingsPerStage.get(stage) ? (
                      <span className="fo-pb-stage-n">{findingsPerStage.get(stage)}</span>
                    ) : null}
                  </div>
                  <div className="fo-pb-stack fo-pb-stack--tight">
                    <span className="fo-pb-stage-name">
                      {t(`board.stage.${stage.toLowerCase()}`)}
                    </span>
                    <span className="fo-pb-stage-q">
                      {t(`board.stageQuestion.${stage.toLowerCase()}`)}
                    </span>
                  </div>
                  <span
                    className="fo-pb-stage-state"
                    style={{
                      color: somethingHappenedAt(stage, reached, findingsPerStage.get(stage) ?? 0)
                        ? 'var(--muted)'
                        : 'var(--line-strong)',
                    }}
                  >
                    {somethingHappenedAt(stage, reached, findingsPerStage.get(stage) ?? 0)
                      ? t('board.ran')
                      : t('board.didNotRun')}
                  </span>
                </button>
                {index < STAGES.length - 1 ? (
                  <span className="fo-pb-link" aria-hidden="true" />
                ) : null}
              </div>
            ))}
          </div>

          {openStage === undefined ? null : (
            <StageDetail
              stage={openStage}
              groups={groups}
              onClose={() => {
                setOpenStage(undefined);
              }}
            />
          )}
        </section>

        <div className="fo-pb-row4">
          <section className="fo-pb-card">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">
                {t('board.decidedEyebrow', { count: readTotal })}
              </span>
              <h3 className="fo-pb-title">{t('board.whatItDecided')}</h3>
            </div>

            <div className="fo-pb-stack fo-pb-stack--tight">
              {analysers.length === 0 ? (
                <p className="fo-pb-quiet">{t('board.nothingRunYet')}</p>
              ) : (
                analysers
                  .filter((line) => line.findings > 0)
                  .map((line) => (
                    <div className="fo-pb-decision" key={line.id}>
                      <span className="fo-pb-decision-key">{line.id}</span>
                      <span
                        className="fo-pb-verdict"
                        style={{ background: 'var(--positive)', color: 'var(--on-positive)' }}
                      >
                        {t('board.saidSomething')}
                      </span>
                      <span className="fo-pb-decision-n">{line.findings}</span>
                    </div>
                  ))
              )}
            </div>

            <p className="fo-pb-note">{t('board.decidedNote')}</p>
          </section>

          <DiscoveredProcesses />

          <section className="fo-pb-card">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">{t('board.silenceEyebrow')}</span>
              <h3 className="fo-pb-title">{t('board.lookedAtSaidNothing')}</h3>
            </div>

            <div className="fo-pb-stack fo-pb-stack--tight">
              {groups.length === 0 ? (
                <p className="fo-pb-quiet">{t('board.nothingDropped')}</p>
              ) : (
                groups.slice(0, 8).map((group) => (
                  <div className="fo-pb-silence" key={`${group.lastStage}-${group.reason}`}>
                    <span
                      className="fo-pb-dot"
                      style={{ background: stageColour(group.lastStage) }}
                    />
                    <span className="fo-pb-silence-stage">
                      {t(`board.stage.${group.lastStage.toLowerCase()}`)}
                    </span>
                    <span className="fo-pb-silence-reason" title={group.reason}>
                      {group.reason}
                    </span>
                    <span className="fo-pb-mono">{group.count}</span>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>

        <section className="fo-pb-card">
          <div className="fo-pb-between fo-pb-between--top">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">
                {analysis.data
                  ? t('board.lookedAtWindow', {
                      from: day(analysis.data.windowFrom),
                      to: day(analysis.data.windowTo),
                    })
                  : t('board.noWindowYet')}
              </span>
              <h3 className="fo-pb-title">{t('board.theAnalysers')}</h3>
              <p className="fo-pb-lede">{t('board.analysersLede')}</p>
            </div>

            <button
              type="button"
              className="ui-button ui-button-secondary"
              disabled={runAnalysers.isPending}
              onClick={() => {
                runAnalysers.mutate(undefined);
              }}
            >
              {runAnalysers.isPending ? t('board.running') : t('board.runTheAnalysers')}
            </button>
          </div>

          <div className="fo-pb-stack fo-pb-stack--tight">
            {analysers.map((line) => (
              <AnalyserRow
                key={line.id}
                line={line}
                open={line.id === chosen}
                onToggle={() => {
                  setChosen((was) => (was === line.id ? undefined : line.id));
                }}
              />
            ))}
          </div>
        </section>

        <section className="fo-pb-card fo-pb-cold">
          <div className="fo-pb-stack fo-pb-stack--tight">
            <span className="fo-pb-eyebrow">{t('board.coldStart')}</span>
            <h3 className="fo-pb-title">{t('board.writeItDown')}</h3>
            <p className="fo-pb-lede">{t('board.coldStartLede')}</p>
          </div>
          <PipelineCompositionControl />
        </section>

        {(history.data ?? []).length > 0 ? (
          <section className="fo-pb-card">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">{t('board.pastRunsEyebrow')}</span>
              <h3 className="fo-pb-title">{t('board.pastRuns')}</h3>
            </div>

            <div className="fo-pb-stack fo-pb-stack--tight">
              {(history.data ?? []).slice(0, 6).map((record) => (
                <div className="fo-pb-decision" key={record.id}>
                  <span className="fo-pb-run-when">{when(record.startedAt)}</span>
                  <span className="fo-pb-mono fo-pb-faint-ink">
                    {t('board.nPiecesRead', { count: record.nodesRead })}
                  </span>
                  <span className="fo-pb-mono fo-pb-faint-ink" title={t('board.signatureMeaning')}>
                    {record.signature.slice(0, 6)}
                  </span>
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </div>

      <aside className="fo-pb-side">
        <div className="fo-pb-ins">
          <div className="fo-pb-between fo-pb-between--top">
            <div className="fo-pb-stack fo-pb-stack--tight">
              <span className="fo-pb-eyebrow">
                {selected ? t('board.oneAnalyser') : t('board.readingThePage')}
              </span>
              <h3 className="fo-pb-title">
                {selected ? selected.id : t('board.whatTheEdgesMean')}
              </h3>
            </div>
            {selected ? (
              <button
                type="button"
                className="fo-pb-close"
                aria-label={t('board.clearSelection')}
                onClick={() => {
                  setChosen(undefined);
                }}
              >
                ✕
              </button>
            ) : null}
          </div>

          {selected ? (
            <>
              <div className="fo-pb-row-of-three">
                <div className="fo-pb-ins-stat">
                  <span className="fo-pb-eyebrow">{t('board.read')}</span>
                  <span className="fo-pb-ins-stat-n">{selected.itemsRead}</span>
                </div>
                <div className="fo-pb-ins-stat">
                  <span className="fo-pb-eyebrow">{t('board.findings')}</span>
                  <span className="fo-pb-ins-stat-n">{selected.findings}</span>
                </div>
                <div className="fo-pb-ins-stat">
                  <span className="fo-pb-eyebrow">{t('board.floor')}</span>
                  <span className="fo-pb-ins-stat-n">
                    {selected.preconditions.filter((pre) => pre.met).length}/
                    {selected.preconditions.length}
                  </span>
                </div>
              </div>

              {selected.preconditions.map((pre) => (
                <div className="fo-pb-ins-teach" key={pre.needed}>
                  <span
                    className="fo-pb-ins-spine"
                    style={{ background: pre.met ? 'var(--positive)' : 'var(--warning)' }}
                  />
                  <span>
                    <strong>{pre.needed}</strong> — {pre.had}
                    {pre.remedy === null ? '' : ` · ${pre.remedy}`}
                  </span>
                </div>
              ))}
            </>
          ) : (
            <>
              <p className="fo-pb-lede">{t('board.inspectorIdle')}</p>
              <Teach tone="var(--warning)" body={t('board.teachAmber')} />
              <Teach tone="var(--line-strong)" body={t('board.teachGrey')} />
              <Teach tone="var(--positive)" body={t('board.teachGreen')} />
            </>
          )}
        </div>
      </aside>
    </div>
  );
}

function AnalyserRow({
  line,
  open,
  onToggle,
}: {
  line: AnalyserLine;
  open: boolean;
  onToggle: () => void;
}): JSX.Element {
  const { t } = useTranslation();
  const state = stateOf(line);
  const met = line.preconditions.filter((pre) => pre.met).length;

  return (
    <button
      type="button"
      className={open ? 'fo-pb-analyser fo-pb-analyser--on' : 'fo-pb-analyser'}
      aria-expanded={open}
      onClick={onToggle}
    >
      <span className="fo-pb-analyser-edge" style={{ background: state.edge }} />
      <span className="fo-pb-analyser-body">
        <span className="fo-pb-analyser-grid">
          <span className="fo-pb-stack fo-pb-stack--tight">
            <span className="fo-pb-chips">
              <span className="fo-pb-analyser-id">{line.id}</span>

              <span
                className="fo-pb-analyser-state"
                style={{ background: state.chip, color: state.chipInk }}
              >
                {t(state.label)}
              </span>
            </span>
          </span>

          <span className="fo-pb-analyser-payload">{payloadOf(line, t)}</span>

          <span className="fo-pb-stack fo-pb-stack--tight">
            <span className="fo-pb-analyser-readout">
              <span className="fo-pb-analyser-meter" aria-hidden="true">
                <span style={{ height: '40%' }} />
                <span style={{ height: '65%' }} />
                <span style={{ height: '50%' }} />
                <span style={{ height: '100%', background: state.edge }} />
              </span>
              <span className="fo-pb-mono">
                {t('board.readNf', { read: line.itemsRead, findings: line.findings })}
              </span>
            </span>
            <span className="fo-pb-floor">
              <span
                style={{
                  width: share(met, Math.max(1, line.preconditions.length)),
                  background:
                    met === line.preconditions.length ? 'var(--positive)' : 'var(--warning)',
                }}
              />
            </span>
            <span className="fo-pb-mono fo-pb-right">
              {t('board.floorMet', { met, total: line.preconditions.length })}
            </span>
          </span>
        </span>

        {open ? (
          <span className="fo-pb-blind">
            <span className="fo-pb-eyebrow">{t('board.couldNotSee')}</span>
            {line.absences.length === 0 ? (
              <span className="fo-pb-blind-line">{t('board.nothingUnseen')}</span>
            ) : (
              line.absences.map((absence) => (
                <span className="fo-pb-blind-line" key={absence.what}>
                  {absence.detail}
                </span>
              ))
            )}
          </span>
        ) : null}
      </span>
    </button>
  );
}

function Teach({ tone, body }: { tone: string; body: string }): JSX.Element {
  return (
    <p className="fo-pb-ins-teach">
      <span className="fo-pb-ins-spine" style={{ background: tone }} />
      <span>{body}</span>
    </p>
  );
}

interface Health {
  total: number;
  working: number;
  blocked: number;
  quiet: number;
  remedies: { text: string; n: number }[];
}

function tally(analysers: readonly AnalyserLine[]): Health {
  const remedies = new Map<string, number>();
  let working = 0;
  let blocked = 0;
  let quiet = 0;

  for (const line of analysers) {
    const unmet = line.preconditions.filter((pre) => !pre.met);
    if (unmet.length > 0) {
      blocked += 1;
      for (const pre of unmet) {
        const text = pre.remedy ?? pre.needed;
        remedies.set(text, (remedies.get(text) ?? 0) + 1);
      }
    } else if (line.findings > 0) {
      working += 1;
    } else {
      quiet += 1;
    }
  }

  return {
    total: analysers.length,
    working,
    blocked,
    quiet,
    remedies: [...remedies.entries()]
      .map(([text, n]) => ({ text, n }))
      .sort((a, b) => b.n - a.n || a.text.localeCompare(b.text)),
  };
}

function stateOf(line: AnalyserLine): {
  edge: string;
  chip: string;
  chipInk: string;
  label: string;
} {
  if (line.failure !== null) {
    return {
      edge: 'var(--critical)',
      chip: 'var(--critical)',
      chipInk: 'var(--on-critical)',
      label: 'board.state.threw',
    };
  }
  if (line.preconditions.some((pre) => !pre.met)) {
    return {
      edge: 'var(--warning)',
      chip: 'var(--warning)',
      chipInk: 'var(--on-warning)',
      label: 'board.state.blocked',
    };
  }
  if (line.findings > 0) {
    return {
      edge: 'var(--positive)',
      chip: 'var(--positive)',
      chipInk: 'var(--on-positive)',
      label: 'board.state.found',
    };
  }
  return {
    edge: 'var(--line-strong)',
    chip: 'var(--surface)',
    chipInk: 'var(--muted)',
    label: 'board.state.quiet',
  };
}

function payloadOf(
  line: AnalyserLine,
  t: (key: string, vars?: Record<string, unknown>) => string,
): string {
  const unmet = line.preconditions.find((pre) => !pre.met);
  if (line.failure !== null) {
    return line.failure;
  }
  if (unmet !== undefined) {
    return t('board.needsThis', { needed: unmet.needed, had: unmet.had });
  }
  if (line.findings > 0) {
    return t('board.foundThis', { count: line.findings, read: line.itemsRead });
  }
  return t('board.lookedAndFine', { count: line.itemsRead });
}

function seriesColour(index: number): string {
  const series = [
    'var(--data-3)',
    'var(--data-4)',
    'var(--data-2)',
    'var(--data-1)',
    'var(--data-5)',
    'var(--volt-600)',
  ];
  return series[index % series.length] ?? 'var(--data-3)';
}

function stageColour(stage: Stage): string {
  const order: Record<Stage, string> = {
    OBSERVE: 'var(--data-1)',
    NORMALISE: 'var(--data-4)',
    MEASURE: 'var(--data-3)',
    DETECT: 'var(--data-2)',
    CORRELATE: 'var(--data-5)',
    RECOMMEND: 'var(--volt-600)',
  };
  return order[stage];
}

function stageGlyph(stage: Stage): string {
  const glyphs: Record<Stage, string> = {
    OBSERVE: '◈',
    NORMALISE: '▤',
    MEASURE: '◐',
    DETECT: '✦',
    CORRELATE: '◆',
    RECOMMEND: '▦',
  };
  return glyphs[stage];
}

function share(part: number, whole: number): string {
  if (whole <= 0) {
    return '0%';
  }
  return `${String(Math.max(0, Math.min(100, Math.round((part / whole) * 100))))}%`;
}

function donut(health: Health): string {
  const total = Math.max(1, health.total);
  const workingEnd = (health.working / total) * 100;
  const blockedEnd = workingEnd + (health.blocked / total) * 100;
  return `conic-gradient(var(--ink-900) 0 ${String(workingEnd)}%, var(--on-warning) ${String(workingEnd)}% ${String(blockedEnd)}%, rgb(11 11 12 / 16%) ${String(blockedEnd)}% 100%)`;
}

function day(iso: string): string {
  return iso.slice(0, 10);
}

function when(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ');
}
