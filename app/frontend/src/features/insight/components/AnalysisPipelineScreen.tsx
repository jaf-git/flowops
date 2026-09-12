import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { AnalyserPanel } from './AnalyserPanel';
import { ComparePanel } from './ComparePanel';
import { AnalysisStrip } from './AnalysisStrip';
import { FindingDrawer } from './FindingDrawer';
import { NodePipelineOutcomes } from './NodePipelineOutcomes';
import { NodePipelineRunControl } from './NodePipelineRunControl';
import { RunEverything } from './RunEverything';
import { PipelineCompositionControl } from './PipelineCompositionControl';
import { RunHistoryPanel } from './RunHistoryPanel';
import { StageBoard } from './StageBoard';
import { StuckPanel } from './StuckPanel';
import {
  useFindings,
  useLatestAnalysis,
  useLatestNodeRun,
  useLatestRun,
  useRecommendations,
  useRunAnalysers,
  useRunAnalysis,
  useStuck,
} from '../hooks/useAnalysis';

export function AnalysisPipelineScreen(): JSX.Element {
  const { t } = useTranslation();
  const findings = useFindings();
  const proposals = useRecommendations();
  const latestRun = useLatestRun();
  const stuck = useStuck();

  const analysis = useLatestAnalysis();
  const runAnalysers = useRunAnalysers();
  const run = useRunAnalysis();

  const [selected, setSelected] = useState<string | undefined>(undefined);

  const rows = findings.data ?? [];
  const chosen = rows.find((finding) => finding.id === selected);

  const lastRun = latestRun.data;
  const nodeRun = useLatestNodeRun();

  return (
    <div className="fo-pipe">
      <div className="fo-pipe-head">
        <AnalysisStrip run={lastRun} findings={rows} />

        <RunEverything />
      </div>

      <details className="fo-runone">
        <summary>{t('pipeline.runOne.title')}</summary>
        <p className="fo-runone-lede">{t('pipeline.runOne.lede')}</p>
        <div className="fo-runone-row">
          <button
            type="button"
            className="ui-button ui-button-secondary"
            disabled={run.isPending}
            onClick={() => {
              run.mutate();
            }}
          >
            {run.isPending ? t('pipeline.running') : t('pipeline.run')}
          </button>
          <span className="fo-runone-what">{t('pipeline.runOne.analysis')}</span>
        </div>
      </details>

      {findings.isError ? (
        <p className="fo-pipe-quiet">{t('pipeline.failed')}</p>
      ) : findings.isPending ? (
        <p className="fo-pipe-quiet">{t('pipeline.loading')}</p>
      ) : (
        <StageBoard
          findings={rows}

          reached={lastRun?.reached ?? 'RECOMMEND'}
          recommendations={proposals.data ?? []}
          selected={selected}
          onSelect={(id) => {
            setSelected((was) => (was === id ? undefined : id));
          }}
        />
      )}

      <NodePipelineRunControl />

      <PipelineCompositionControl />

      <NodePipelineOutcomes />

      <ComparePanel runId={nodeRun.data?.run.id} />

      <StuckPanel groups={stuck.data ?? []} />

      <AnalyserPanel
        run={analysis.data ?? null}
        onRun={() => runAnalysers.mutate(undefined)}
        running={runAnalysers.isPending}
      />

      <RunHistoryPanel />

      <FindingDrawer
        finding={chosen}
        onClose={() => {
          setSelected(undefined);
        }}
      />
    </div>
  );
}
