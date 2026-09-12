import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { StageTally } from '../api/nodePipelineApi';
import { useLatestNodeRun } from '../hooks/useAnalysis';

export function NodePipelineOutcomes(): JSX.Element | null {
  const { t } = useTranslation();
  const latest = useLatestNodeRun();

  if (latest.isPending || latest.isError || latest.data === null || latest.data === undefined) {
    return null;
  }

  const { run, stages } = latest.data;
  if (stages.length === 0) {
    return null;
  }

  return (
    <section className="fo-outcomes" aria-labelledby="fo-outcomes-title">
      <h2 className="fo-outcomes-title" id="fo-outcomes-title">
        {t('pipeline.outcomes.title')}
      </h2>

      <p className="fo-outcomes-eyebrow">
        {run.nodesInWindow > run.nodesRead
          ? t('pipeline.outcomes.readOfWindow', { read: run.nodesRead, held: run.nodesInWindow })
          : t('pipeline.outcomes.read', { count: run.nodesRead })}{' '}
        · {run.signature.slice(0, 6)}
      </p>
      {run.nodesInWindow > run.nodesRead ? (
        <p className="fo-outcomes-capped">
          {t('pipeline.outcomes.capped', { read: run.nodesRead })}
        </p>
      ) : null}

      <ul className="fo-outcomes-list">
        {byStage(stages).map(([stage, rows]) => (
          <li className="fo-outcomes-group" key={stage}>
            <span className="fo-outcomes-stage">{stage}</span>
            <span className="fo-outcomes-tiers">
              {rows.map((row) => (
                <span className="fo-outcomes-tier" key={row.outcome}>
                  <span className="fo-outcomes-tier-name">{row.outcome}</span>
                  <span className="fo-outcomes-tier-count">{row.count}</span>
                </span>
              ))}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function byStage(stages: StageTally[]): [string, StageTally[]][] {
  const grouped = new Map<string, StageTally[]>();
  for (const tally of stages) {
    const rows = grouped.get(tally.stage);
    if (rows === undefined) {
      grouped.set(tally.stage, [tally]);
    } else {
      rows.push(tally);
    }
  }
  return [...grouped.entries()];
}
