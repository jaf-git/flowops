import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { durationLabel } from '../lib/elapsed';

export type Phase = 'active' | 'blocked' | 'wait' | 'review' | 'approval';

export interface PhaseSpan {
  phase: Phase;
  seconds: number;
}

const ORDER: readonly Phase[] = ['active', 'blocked', 'wait', 'review', 'approval'];

const FILL: Record<Phase, string> = {
  active: 'var(--data-2)',
  blocked: 'var(--data-6)',
  wait: 'var(--data-5)',
  review: 'var(--data-3)',
  approval: 'var(--data-1)',
};

interface PhaseBreakdownProps {
  spans: readonly PhaseSpan[];

  variant?: 'labels' | 'bar';
}

export function PhaseBreakdown({ spans, variant = 'labels' }: PhaseBreakdownProps): JSX.Element {
  const { t, i18n } = useTranslation();

  const totals = new Map<Phase, number>();

  for (const span of spans) {
    totals.set(span.phase, (totals.get(span.phase) ?? 0) + span.seconds);
  }

  const segments = ORDER.filter((phase) => (totals.get(phase) ?? 0) > 0).map((phase) => ({
    phase,
    seconds: totals.get(phase) ?? 0,
    label: t(`ui.phase.${phase}`, {
      duration: durationLabel(totals.get(phase) ?? 0, i18n.language),
    }),
  }));

  if (segments.length === 0) {
    return <p className="ui-phase-list">{t('ui.phase.none')}</p>;
  }

  const words = (
    <p className="ui-phase-list" aria-label={t('ui.phase.heading')}>
      {segments.map((segment, index) => (
        <span key={segment.phase} className="ui-phase fo-phase">
          {index > 0 && (
            <span className="ui-phase-separator" aria-hidden="true">
              {' · '}
            </span>
          )}
          <span className={segment.phase === 'active' ? 'ui-phase-active' : undefined}>
            {segment.label}
          </span>
        </span>
      ))}
    </p>
  );

  if (variant === 'labels') {
    return words;
  }

  const drawn = segments.reduce((sum, segment) => sum + segment.seconds, 0);

  return (
    <span className="ui-phase-figure">
      <span className="ui-phase-bar" aria-hidden="true">
        {segments.map((segment) => (
          <span
            key={segment.phase}
            className="ui-phase-bar-segment"
            data-testid="phase-segment"
            data-phase={segment.phase}

            style={{
              width: `${(segment.seconds / drawn) * 100}%`,
              background: FILL[segment.phase],
            }}
          />
        ))}
      </span>
      {words}
    </span>
  );
}
