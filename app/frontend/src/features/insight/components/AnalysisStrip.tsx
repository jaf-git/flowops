import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { CountUp } from '../../../shared/motion/CountUp';
import { STAGES, describeStage, type Finding, type StoredRun } from '../api/analysisApi';

interface AnalysisStripProps {
  readonly run: StoredRun | null | undefined;
  readonly findings: readonly Finding[];
}

export function AnalysisStrip({ run, findings }: AnalysisStripProps): JSX.Element {
  const { t } = useTranslation();

  const shapes = findings.filter((finding) => finding.subjectKind === 'SHAPE').length;

  const byStage = STAGES.map((stage) => ({
    stage,
    count: findings.filter((finding) => finding.stage === stage).length,
  }));

  const total = byStage.reduce((sum, entry) => sum + entry.count, 0);

  const neverRun = run === null || run === undefined;

  return (
    <div className="fo-pipe-strip">
      <Figure value={neverRun ? null : run.bracketsRead} caption={t('pipeline.kpi.read')} />
      <Rule />
      <Figure value={neverRun ? null : findings.length} caption={t('pipeline.kpi.findings')} />
      <Rule />
      <Figure value={neverRun ? null : shapes} caption={t('pipeline.kpi.shapes')} />

      <p className="fo-pipe-strip-source">
        {neverRun ? t('pipeline.kpi.neverRun') : t('pipeline.kpi.source')}
      </p>

      <div className="fo-pipe-ribbon">
        <div className="fo-pipe-ribbon-head">
          <span>{t('pipeline.ribbon.title')}</span>

          <span className="fo-pipe-ribbon-key">{t('pipeline.ribbon.key')}</span>
        </div>

        <div className="fo-pipe-ribbon-bar">
          {byStage.map((entry) => (
            <span
              key={entry.stage}
              className="fo-pipe-ribbon-seg"
              data-stage={entry.stage}

              style={{
                flexGrow: total === 0 ? 1 : Math.max(entry.count / total, 0.06),
              }}
              title={`${describeStage(entry.stage).name}: ${String(entry.count)}`}
              data-empty={entry.count === 0}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

function Figure({ value, caption }: { value: number | null; caption: string }): JSX.Element {
  return (
    <div className="fo-pipe-figure">
      <span className="fo-pipe-figure-value">
        {value === null ? '—' : <CountUp value={value} />}
      </span>
      <span className="fo-pipe-figure-caption">{caption}</span>
    </div>
  );
}

function Rule(): JSX.Element {
  return <span aria-hidden="true" className="fo-pipe-rule" />;
}
