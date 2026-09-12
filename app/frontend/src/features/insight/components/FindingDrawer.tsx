import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { shapeInWords } from '../api/analysisApi';

import { describeStage, type Finding } from '../api/analysisApi';
import { useFindingSubjects } from '../hooks/useAnalysis';

interface FindingDrawerProps {
  readonly finding: Finding | undefined;
  readonly onClose: () => void;
}

export function FindingDrawer({ finding, onClose }: FindingDrawerProps): JSX.Element {
  const { t } = useTranslation();
  const subjects = useFindingSubjects(finding?.id);

  if (finding === undefined) {
    return (
      <aside className="fo-pipe-drawer" data-open="false">
        <p className="fo-pipe-drawer-hint">{t('pipeline.drawer.pick')}</p>
      </aside>
    );
  }

  const stage = describeStage(finding.stage);

  return (
    <aside className="fo-pipe-drawer" data-open="true" aria-label={t('pipeline.drawer.label')}>
      <header className="fo-pipe-drawer-head">
        <span className="fo-pipe-drawer-eyebrow">
          {stage.name} · {finding.detector}
        </span>

        <button
          type="button"
          className="fo-pipe-drawer-close"
          aria-label={t('pipeline.drawer.close')}
          onClick={onClose}
        >
          <span aria-hidden="true">±</span>
        </button>
      </header>

      <p className="fo-pipe-drawer-says">{finding.phrasing ?? finding.headline}</p>

      {finding.phrasing === null ? null : <p className="fo-pipe-drawer-raw">{finding.headline}</p>}

      <dl className="fo-pipe-drawer-facts">
        <dt>{t('pipeline.drawer.evidence')}</dt>
        <dd>{t('pipeline.card.evidence', { count: finding.sampleSize })}</dd>

        <dt>{t('pipeline.drawer.about')}</dt>

        <dd>{shapeInWords(finding.subjectKey)}</dd>

        <dt>{t('pipeline.drawer.phrasedBy')}</dt>
        <dd>
          {finding.phrasedBy === 'MODEL'
            ? t('pipeline.drawer.byModel')
            : t('pipeline.drawer.byTemplate')}
        </dd>
      </dl>

      <p className="fo-pipe-drawer-graph">
        {subjects.isPending
          ? t('pipeline.drawer.loadingGraph')
          : t('pipeline.drawer.drawnFrom', { count: subjects.data?.length ?? 0 })}
      </p>
    </aside>
  );
}
