import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useAnnounce } from '../../../shared/notice/useNotices';
import { Button } from '../../../shared/ui/Button';
import { StageRail, type RailStage, type StageState } from '../../../shared/ui/StageRail';
import { useRunAnalysers, useRunAnalysis, useRunNodePipeline } from '../hooks/useAnalysis';

const DEFAULT_DAYS = 30;

const PASSES = ['nodes', 'analysis', 'analysers'] as const;

type Pass = (typeof PASSES)[number];

type Progress = Readonly<Record<Pass, StageState>>;

const NOTHING_RUN: Progress = { nodes: 'waiting', analysis: 'waiting', analysers: 'waiting' };

type Outcomes = Readonly<Partial<Record<Pass, string>>>;

export function RunEverything(): JSX.Element {
  const { t } = useTranslation();
  const announce = useAnnounce();

  const nodePipeline = useRunNodePipeline();
  const analysis = useRunAnalysis();
  const analysers = useRunAnalysers();

  const [progress, setProgress] = useState<Progress>(NOTHING_RUN);
  const [outcomes, setOutcomes] = useState<Outcomes>({});

  const running = PASSES.some((pass) => progress[pass] === 'running');
  const everythingRan = PASSES.every((pass) => progress[pass] === 'done');

  const stages: RailStage[] = PASSES.map((pass) => ({
    id: pass,
    name: t(`pipeline.runAll.${pass}`),
    state: progress[pass],
    outcome: outcomes[pass],
  }));

  async function runAll(): Promise<void> {
    setProgress(NOTHING_RUN);
    setOutcomes({});

    let at: Pass = 'nodes';
    const enter = (pass: Pass): void => {
      at = pass;
      setProgress((held) => ({ ...held, [pass]: 'running' }));
    };
    const finish = (pass: Pass, outcome: string): void => {
      setProgress((held) => ({ ...held, [pass]: 'done' }));
      setOutcomes((held) => ({ ...held, [pass]: outcome }));
    };

    try {
      enter('nodes');
      const nodes = await nodePipeline.mutateAsync(DEFAULT_DAYS);
      finish('nodes', t('pipeline.runAll.nodesRead', { count: nodes.nodesRead }));

      enter('analysis');
      const analysed = await analysis.mutateAsync(undefined);
      finish('analysis', t('pipeline.runAll.bracketsRead', { count: analysed.bracketsRead }));

      enter('analysers');
      await analysers.mutateAsync(undefined);
      finish('analysers', t('pipeline.runAll.analysersRan'));

      announce({
        tone: 'done',
        message: t('pipeline.runAll.done'),
        detail: t('pipeline.runAll.nodesRead', { count: nodes.nodesRead }),
      });
    } catch {
      setProgress((held) => ({ ...held, [at]: 'stopped' }));

      announce({
        tone: 'failed',
        message: t('pipeline.runAll.failed', { stage: t(`pipeline.runAll.${at}`) }),
      });
    }
  }

  return (
    <div className="fo-runall">
      <Button
        variant="primary"
        loading={running}
        loadingLabel={t('pipeline.runAll.running')}
        onClick={() => {
          void runAll();
        }}
      >
        {t('pipeline.runAll.run')}
      </Button>

      <p className="fo-runall-lede">{t('pipeline.runAll.lede')}</p>

      <StageRail stages={stages} label={t('pipeline.runAll.progress')} />

      {everythingRan && (
        <p className="fo-runall-done" aria-live="polite">
          {t('pipeline.runAll.done')}
        </p>
      )}
    </div>
  );
}
