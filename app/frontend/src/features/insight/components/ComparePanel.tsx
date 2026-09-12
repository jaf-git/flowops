import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useRunComparison } from '../hooks/useAnalysis';

interface ComparePanelProps {
  readonly runId: string | undefined;
}

export function ComparePanel({ runId }: ComparePanelProps): JSX.Element | null {
  const { t } = useTranslation();
  const comparison = useRunComparison(runId);

  if (comparison.data === null || comparison.data === undefined) {
    return null;
  }

  const { compared, agreed, raised, lowered, changed, failed, modelId } = comparison.data;

  const buckets: readonly { readonly key: string; readonly value: number }[] = [
    { key: 'agreed', value: agreed },
    { key: 'raised', value: raised },
    { key: 'lowered', value: lowered },
    { key: 'changed', value: changed },
    { key: 'failed', value: failed },
  ];

  return (
    <section className="fo-compare" aria-labelledby="fo-compare-title">
      <div className="fo-compare__head">
        <h3 className="fo-compare__title" id="fo-compare-title">
          {t('pipeline.compare.title')}
        </h3>
        <p className="fo-compare__why">{t('pipeline.compare.why', { count: compared })}</p>
      </div>

      <dl className="fo-compare__buckets">
        {buckets.map(({ key, value }) => (
          <div className="fo-compare__bucket" key={key}>
            <dt className="fo-compare__bucket-label">{t(`pipeline.compare.bucket.${key}`)}</dt>
            <dd className="fo-compare__bucket-value">{value}</dd>
          </div>
        ))}
      </dl>

      <p className="fo-compare__model">
        {modelId === null
          ? t('pipeline.compare.noModel')
          : t('pipeline.compare.model', { model: modelId })}
      </p>

      <p className="fo-compare__cost">{t('pipeline.compare.cost')}</p>
      <p className="fo-compare__endDate">{t('pipeline.compare.endDate')}</p>
    </section>
  );
}
