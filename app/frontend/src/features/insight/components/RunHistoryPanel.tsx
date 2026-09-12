import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { RunRecord } from '../api/nodePipelineApi';
import { stuckReasonInWords } from '../api/stuckWording';
import { useRunDiff, useRunHistory } from '../hooks/useAnalysis';

export function RunHistoryPanel(): JSX.Element {
  const { t } = useTranslation();
  const history = useRunHistory();

  const [chosen, setChosen] = useState<string[]>([]);
  const [before, after] = chosen;
  const pair = before !== undefined && after !== undefined ? { before, after } : undefined;
  const diff = useRunDiff(pair);

  function toggle(id: string): void {
    setChosen((was) =>
      was.includes(id) ? was.filter((each) => each !== id) : [...was, id].slice(-2),
    );
  }

  const runs = history.data ?? [];

  return (
    <section className="fo-runs" aria-labelledby="fo-runs-title">
      <h2 className="fo-runs-title" id="fo-runs-title">
        {t('pipeline.runs.title')}
      </h2>

      {history.isError ? (
        <p className="fo-runs-quiet">{t('pipeline.runs.failed')}</p>
      ) : history.isPending ? (
        <p className="fo-runs-quiet">{t('pipeline.runs.loading')}</p>
      ) : runs.length === 0 ? (
        <p className="fo-runs-quiet">{t('pipeline.runs.none')}</p>
      ) : (
        <>
          <p className="fo-runs-eyebrow">{t('pipeline.runs.pick')}</p>

          <ul className="fo-runs-list">
            {runs.map((run) => (
              <li key={run.id}>
                <button
                  type="button"
                  className="fo-runs-row"
                  aria-pressed={chosen.includes(run.id)}
                  onClick={() => {
                    toggle(run.id);
                  }}
                >
                  <span className="fo-runs-ordinal" aria-hidden={!chosen.includes(run.id)}>
                    {chosen.includes(run.id) ? chosen.indexOf(run.id) + 1 : '·'}
                  </span>
                  <span className="fo-runs-when">{whenInWords(run)}</span>
                  <span className="fo-runs-counts">
                    {t('pipeline.runs.read', { count: run.nodesRead })}
                  </span>

                  <span className="fo-runs-signature">{run.signature.slice(0, 6)}</span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {pair === undefined ? null : diff.isError ? (
        <p className="fo-runs-quiet">{t('pipeline.runs.diffFailed')}</p>
      ) : diff.isPending ? (
        <p className="fo-runs-quiet">{t('pipeline.runs.diffing')}</p>
      ) : diff.data?.length === 0 ? (
        <p className="fo-runs-quiet">{t('pipeline.runs.identical')}</p>
      ) : (
        <ul className="fo-runs-diff">
          {(diff.data ?? []).map((moved) => (
            <li className="fo-runs-moved" key={`${moved.itemKind}-${moved.itemId}`}>
              <span className="fo-runs-kind">{moved.itemKind}</span>
              <span className="fo-runs-moved-from">
                {sideInWords(moved.beforeStage, moved.beforeReason, t)}
              </span>
              <span className="fo-runs-arrow" aria-hidden="true">
                →
              </span>
              <span className="fo-runs-moved-to">
                {sideInWords(moved.afterStage, moved.afterReason, t)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function sideInWords(
  stage: string | null,
  reason: string | null,
  t: (key: string) => string,
): string {
  if (stage === null) {
    return t('pipeline.runs.notRecorded');
  }
  return reason === null ? stage : `${stage} · ${stuckReasonInWords(reason)}`;
}

function whenInWords(run: RunRecord): string {
  return new Date(run.startedAt).toLocaleString();
}
