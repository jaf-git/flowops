import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { durationLabel, relativeLabel } from '../lib/elapsed';
import { Chip } from './Chip';
import { serverNow } from '../lib/serverClock';

export type DeadlineState = 'on-time' | 'at-risk' | 'overdue';

interface DeadlineIndicatorProps {
  dueAt: string | Date | null;

  atRisk?: boolean;

  blockedOn?: string;

  now?: Date;
}

const GLYPH: Record<DeadlineState, string> = {
  'on-time': '✓',
  'at-risk': '●',
  overdue: '●',
};

export function DeadlineIndicator({
  dueAt,
  atRisk = false,
  blockedOn,
  now,
}: DeadlineIndicatorProps): JSX.Element {
  const { t, i18n } = useTranslation();

  if (dueAt === null) {
    return <span className="ui-deadline-empty">{'—'}</span>;
  }
  const due = dueAt instanceof Date ? dueAt : new Date(dueAt);
  const reference = now ?? serverNow();

  const overdue = !Number.isNaN(due.getTime()) && due.getTime() < reference.getTime();
  const state: DeadlineState = overdue ? 'overdue' : atRisk ? 'at-risk' : 'on-time';

  return (
    <span className="ui-deadline">
      <Chip tone={TONE[state]}>
        <span aria-hidden="true" className="ui-chip-state-glyph">
          {GLYPH[state]}
        </span>
        {t(`ui.deadline.${state}`, {
          when: relativeLabel(due, reference, i18n.language),
          amount: durationLabel((reference.getTime() - due.getTime()) / 1000, i18n.language),
        })}
      </Chip>

      {blockedOn !== undefined && (
        <Chip tone="blocked">
          <span aria-hidden="true" className="ui-chip-state-glyph">
            {'▲'}
          </span>
          {t('ui.deadline.blocked', { reason: blockedOn })}
        </Chip>
      )}
    </span>
  );
}

const TONE: Record<DeadlineState, 'neutral' | 'at-risk' | 'waiting'> = {
  'on-time': 'neutral',
  'at-risk': 'at-risk',
  overdue: 'waiting',
};
