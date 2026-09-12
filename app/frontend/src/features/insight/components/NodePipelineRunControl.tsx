import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { useRunNodePipeline, useWindowPreview } from '../hooks/useAnalysis';

export function NodePipelineRunControl(): JSX.Element {
  const { t } = useTranslation();

  const spans = [30, 90, 180, 365];
  const [days, setDays] = useState(30);

  const preview = useWindowPreview(days);
  const run = useRunNodePipeline();

  const held = preview.data?.nodesInWindow;

  return (
    <section className="fo-noderun" aria-labelledby="fo-noderun-title">
      <p className="fo-noderun-eyebrow">{t('pipeline.nodeRun.window')}</p>

      <h2 className="fo-noderun-title" id="fo-noderun-title">
        {t('pipeline.nodeRun.title')}
      </h2>

      <div className="fo-noderun-spans" role="group" aria-labelledby="fo-noderun-title">
        {spans.map((span) => (
          <button
            key={span}
            type="button"
            className="ui-chip"
            aria-pressed={span === days}
            onClick={() => {
              setDays(span);
            }}
          >
            {t('pipeline.nodeRun.days', { count: span })}
          </button>
        ))}
      </div>

      <p className="fo-noderun-count" aria-live="polite">
        {preview.isError
          ? t('pipeline.nodeRun.countFailed', {
              reason: preview.error?.message ?? t('pipeline.nodeRun.reasonUnknown'),
            })
          : preview.isPending
            ? t('pipeline.nodeRun.counting')
            : held === 0
              ? t('pipeline.nodeRun.empty')
              : preview.data?.wouldTruncate
                ? `${t('pipeline.nodeRun.holds', { count: held })} · ${t(
                    'pipeline.nodeRun.capped',
                    {
                      cap: preview.data.cap,
                    },
                  )}`
                : t('pipeline.nodeRun.holds', { count: held })}
      </p>

      <p className="fo-noderun-lede">{t('pipeline.nodeRun.lede')}</p>

      <Button
        variant="quiet"
        loading={run.isPending}
        loadingLabel={t('pipeline.nodeRun.running')}

        disabled={held === 0}
        onClick={() => {
          run.mutate(days);
        }}
      >
        {t('pipeline.nodeRun.run')}
      </Button>

      {run.isError ? (
        <p className="fo-noderun-failed" role="alert">
          {t('pipeline.nodeRun.failed')}
        </p>
      ) : run.data ? (
        <p className="fo-noderun-result" aria-live="polite">
          {t('pipeline.nodeRun.result', {
            read: run.data.nodesRead,
            held: held ?? run.data.nodesRead,
            kinds: run.data.stepKindsFound,
            processes: run.data.processesDiscovered,
          })}
          {run.data.discoveryCandidates > 0
            ? ` · ${t('pipeline.nodeRun.candidates', { count: run.data.discoveryCandidates })}`
            : ''}
        </p>
      ) : null}
    </section>
  );
}
