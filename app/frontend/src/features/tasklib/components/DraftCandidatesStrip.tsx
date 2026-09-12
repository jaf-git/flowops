import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { RelativeTime } from '../../../shared/ui/RelativeTime';
import type { DraftCandidate } from '../api/taskTemplateApi';

interface DraftCandidatesStripProps {
  candidates: readonly DraftCandidate[];
}

export function DraftCandidatesStrip({
  candidates,
}: DraftCandidatesStripProps): JSX.Element | null {
  const { t } = useTranslation();

  if (candidates.length === 0) {
    return null;
  }

  return (
    <section
      aria-label={t('tasklib.candidates.heading')}
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-2)' }}>
        <h3 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 600 }}>
          {t('tasklib.candidates.heading')}
        </h3>
        <span style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
          {t('tasklib.candidates.explain')}
        </span>
      </div>

      <ul
        style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 'var(--space-2)' }}
      >
        {candidates.map((candidate) => (
          <li
            key={candidate.templateIds[0]}
            style={{
              border: '1px solid var(--line)',
              borderRadius: 'var(--radius-sm)',
              padding: 'var(--space-3)',
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-1)',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                gap: 'var(--space-3)',
                alignItems: 'baseline',
              }}
            >
              <strong>{candidate.title}</strong>
              <span
                style={{
                  color: 'var(--muted)',
                  fontSize: 'var(--text-sm)',
                  whiteSpace: 'nowrap',
                }}
              >
                {t('tasklib.candidates.typed', { count: candidate.drafts })}
              </span>
            </div>

            <span style={{ color: 'var(--muted)', fontSize: 'var(--text-xs)' }}>
              {t('tasklib.candidates.firstSeen')} <RelativeTime value={candidate.firstSeen} />
              {' · '}
              {t('tasklib.candidates.lastSeen')} <RelativeTime value={candidate.lastSeen} />
            </span>

            {candidate.variants.length > 1 ? (
              <span style={{ color: 'var(--muted)', fontSize: 'var(--text-xs)' }}>
                {t('tasklib.candidates.alsoTyped')} {candidate.variants.join(' · ')}
              </span>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
