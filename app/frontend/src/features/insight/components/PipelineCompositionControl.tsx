import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { useComposeDiscoveries } from '../hooks/useAnalysis';

export function PipelineCompositionControl(): JSX.Element {
  const { t } = useTranslation();
  const compose = useComposeDiscoveries();

  const written = compose.data;

  const foundNothing = written !== undefined && written.stepKindsFound === 0;

  return (
    <section className="fo-compose" aria-labelledby="fo-compose-title">
      <p className="fo-compose-eyebrow">{t('pipeline.compose.eyebrow')}</p>

      <h2 className="fo-compose-title" id="fo-compose-title">
        {t('pipeline.compose.title')}
      </h2>

      <p className="fo-compose-lede">{t('pipeline.compose.lede')}</p>

      <Button
        variant="quiet"
        loading={compose.isPending}
        loadingLabel={t('pipeline.compose.writing')}
        onClick={() => {
          compose.mutate();
        }}
      >
        {t('pipeline.compose.write')}
      </Button>

      {compose.isError ? (
        <p className="fo-compose-failed" role="alert">
          {t('pipeline.compose.failed')}
        </p>
      ) : null}

      {foundNothing ? (
        <p className="fo-compose-nothing" aria-live="polite">
          {t('pipeline.compose.nothing')}
        </p>
      ) : null}

      {written !== undefined && !foundNothing ? (
        <div className="fo-compose-written" aria-live="polite">
          <p className="fo-compose-summary">
            {t('pipeline.compose.summary', {
              kinds: written.stepKindsFound,
              drafted: written.taskTemplatesDrafted,
              processes: written.processes.length,
            })}
          </p>

          {written.processes.length > 0 ? (
            <ul className="fo-compose-list">
              {written.processes.map((composed) => (
                <li className="fo-compose-item" key={composed.templateId}>
                  <p className="fo-compose-item-name">{composed.name}</p>
                  <p className="fo-compose-item-meta">
                    {t('pipeline.compose.shape', {
                      steps: composed.steps,
                      runs: composed.seenInRuns,
                    })}
                  </p>

                  {composed.blocksApproval.length > 0 ? (
                    <ul className="fo-compose-blockers">
                      {composed.blocksApproval.map((reason) => (
                        <li key={reason}>{reason}</li>
                      ))}
                    </ul>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}

          {written.alreadyThere.length > 0 ? (
            <p className="fo-compose-already">
              {t('pipeline.compose.alreadyThere', { names: written.alreadyThere.join(', ') })}
            </p>
          ) : null}
        </div>
      ) : null}

      <p className="fo-compose-precondition">{t('pipeline.compose.precondition')}</p>
    </section>
  );
}
