import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { Banner } from '../../../shared/ui/Banner';
import { Card } from '../../../shared/ui/Card';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ProgressMeter } from '../../../shared/ui/ProgressMeter';
import { Spinner } from '../../../shared/ui/Spinner';

export interface CanvasIndexEntry {
  id: string;
  name: string;

  state: string;
  closed: number;
  total: number;
}

interface CanvasIndexScreenProps {
  locale: string;
  entries: readonly CanvasIndexEntry[];
  isPending: boolean;
  isError: boolean;
}

export function CanvasIndexScreen({
  locale,
  entries,
  isPending,
  isError,
}: CanvasIndexScreenProps): JSX.Element {
  const { t } = useTranslation();

  if (isPending) {
    return <Spinner label={t('canvas.index.loading')} />;
  }

  if (isError) {
    return <Banner tone="alert">{t('canvas.index.loadFailed')}</Banner>;
  }

  if (entries.length === 0) {
    return (
      <EmptyState heading={t('canvas.index.empty.heading')} body={t('canvas.index.empty.body')} />
    );
  }

  return (
    <section aria-label={t('canvas.index.heading')}>
      <h1>{t('canvas.index.heading')}</h1>
      <p style={{ color: 'var(--muted)', margin: '0 0 var(--space-5)' }}>
        {t('canvas.index.explain')}
      </p>
      <ul
        style={{
          listStyle: 'none',
          margin: 0,
          padding: 0,
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 'var(--space-4)',
        }}
      >
        {entries.map((run) => (
          <li key={run.id}>
            <Card>
              <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 700 }}>{run.name}</h2>
              <p style={{ margin: 'var(--space-2) 0', color: 'var(--muted)' }}>
                {t(`process.state.${run.state}`)}
              </p>
              <ProgressMeter
                done={run.closed}
                total={run.total}
                label={t('process.progress.label', { closed: run.closed, total: run.total })}
              />
              <Link
                className="ui-button ui-button-secondary"
                to={`/${locale}/canvas/process/${run.id}`}
                style={{ marginTop: 'var(--space-4)' }}
              >
                {t('canvas.index.open')}
              </Link>
            </Card>
          </li>
        ))}
      </ul>
    </section>
  );
}
