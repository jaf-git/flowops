import type { CSSProperties, JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { relativeLabel } from '../lib/elapsed';
import { serverNow } from '../lib/serverClock';

interface RelativeTimeProps {
  value: string | Date;

  now?: Date;
  className?: string;
}

const VALUE: CSSProperties = {
  fontFamily: 'var(--font-mono)',
  fontSize: 'var(--text-code)',
  fontVariantNumeric: 'tabular-nums',
};

export function RelativeTime({ value, now, className }: RelativeTimeProps): JSX.Element {
  const { i18n } = useTranslation();

  const instant = value instanceof Date ? value : new Date(value);

  if (Number.isNaN(instant.getTime())) {
    return (
      <span className={className} style={VALUE}>
        {String(value)}
      </span>
    );
  }

  const reference = now ?? serverNow();

  return (
    <time
      dateTime={instant.toISOString()}
      title={instant.toLocaleString(i18n.language)}
      className={className}
      style={VALUE}
    >
      {relativeLabel(instant, reference, i18n.language)}
    </time>
  );
}
