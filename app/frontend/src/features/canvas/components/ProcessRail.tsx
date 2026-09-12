import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import type { CanvasIndexEntry } from '../routes/CanvasIndexScreen';

interface ProcessRailProps {
  entries: readonly CanvasIndexEntry[];

  currentInstanceId: string;
  locale: string;
}

export function ProcessRail({ entries, currentInstanceId, locale }: ProcessRailProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <nav
      aria-label={t('canvas.rail.heading')}
      style={{
        width: '260px',
        flexShrink: 0,
        borderRight: '1px solid var(--line)',
        background: 'var(--surface)',
        padding: 'var(--space-4)',
        overflowY: 'auto',
      }}
    >
      <h2
        style={{
          margin: '0 0 var(--space-3)',
          fontSize: 'var(--text-sm)',
          fontWeight: 700,
          color: 'var(--muted)',
        }}
      >
        {t('canvas.rail.heading')}
      </h2>
      <ul
        style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 'var(--space-2)' }}
      >
        {entries.map((entry) => {
          const here = entry.id === currentInstanceId;
          const label = t('process.progress.label', { closed: entry.closed, total: entry.total });

          return (
            <li key={entry.id}>
              {here ? (
                <span
                  aria-current="page"
                  style={{
                    display: 'block',
                    padding: 'var(--space-2) var(--space-3)',
                    borderRadius: 'var(--radius-control)',
                    border: '1px solid var(--brand)',
                    background: 'var(--brand-soft)',
                    color: 'var(--ink)',
                  }}
                >
                  <span style={{ display: 'block', fontWeight: 700 }}>{entry.name}</span>
                  <span style={{ display: 'block', color: 'var(--muted)' }}>{label}</span>
                </span>
              ) : (
                <Link
                  to={`/${locale}/canvas/process/${entry.id}`}
                  style={{
                    display: 'block',
                    padding: 'var(--space-2) var(--space-3)',
                    borderRadius: 'var(--radius-control)',
                    border: '1px solid var(--line)',
                    color: 'var(--ink)',
                    textDecoration: 'none',
                  }}
                >
                  <span style={{ display: 'block', fontWeight: 700 }}>{entry.name}</span>
                  <span style={{ display: 'block', color: 'var(--muted)' }}>{label}</span>
                </Link>
              )}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
