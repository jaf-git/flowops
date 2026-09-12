import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { FindingContext } from '../api/analyserApi';

export function FindingContextLine({
  context,
}: {
  readonly context: FindingContext | null;
}): JSX.Element | null {
  const { t } = useTranslation();

  if (context === null) {
    return null;
  }

  const parts: string[] = [];

  if (context.engagements > 0) {
    parts.push(t('queue.context.engagementCount', { count: context.engagements }));
  }

  parts.push(context.clients.length > 0 ? context.clients.join(', ') : t('queue.context.noClient'));

  if (context.projects.length > 0) {
    parts.push(context.projects.join(', '));
  }

  if (context.workTypes.length > 0) {
    parts.push(context.workTypes.join(', '));
  }

  const span = spanIn(context, t);
  if (span !== null) {
    parts.push(span);
  }

  if (parts.length === 0) {
    return null;
  }

  return <p className="fo-finding__context">{parts.join(' · ')}</p>;
}

function spanIn(
  context: FindingContext,
  t: (key: string, options?: Record<string, unknown>) => string,
): string | null {
  if (context.from === null || context.to === null) {
    return null;
  }
  const from = new Date(context.from).getTime();
  const to = new Date(context.to).getTime();
  if (Number.isNaN(from) || Number.isNaN(to)) {
    return null;
  }

  const days = Math.round((to - from) / (24 * 60 * 60 * 1000));
  return days <= 0 ? t('queue.context.sameDay') : t('queue.context.overDays', { count: days });
}
