const UNITS: ReadonlyArray<{ unit: Intl.RelativeTimeFormatUnit; seconds: number }> = [
  { unit: 'year', seconds: 60 * 60 * 24 * 365 },
  { unit: 'month', seconds: 60 * 60 * 24 * 30 },
  { unit: 'week', seconds: 60 * 60 * 24 * 7 },
  { unit: 'day', seconds: 60 * 60 * 24 },
  { unit: 'hour', seconds: 60 * 60 },
  { unit: 'minute', seconds: 60 },
  { unit: 'second', seconds: 1 },
];

export function relativeLabel(instant: Date, now: Date, locale: string): string {
  const elapsedSeconds = (instant.getTime() - now.getTime()) / 1000;

  const { unit, seconds } = UNITS.find(({ seconds: size }) => Math.abs(elapsedSeconds) >= size) ?? {
    unit: 'second' as Intl.RelativeTimeFormatUnit,
    seconds: 1,
  };

  const amount = Math.trunc(elapsedSeconds / seconds);

  return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(amount, unit);
}

const DURATION_UNITS: ReadonlyArray<{ unit: 'day' | 'hour' | 'minute'; seconds: number }> = [
  { unit: 'day', seconds: 60 * 60 * 24 },
  { unit: 'hour', seconds: 60 * 60 },
  { unit: 'minute', seconds: 60 },
];

export function durationLabel(seconds: number, locale: string): string {
  const magnitude = Math.abs(seconds);

  const { unit, seconds: size } = DURATION_UNITS.find(({ seconds: step }) => magnitude >= step) ?? {
    unit: 'minute' as const,
    seconds: 60,
  };

  const amount = Math.max(Math.round((magnitude / size) * 10) / 10, magnitude > 0 ? 0.1 : 0);

  return new Intl.NumberFormat(locale, {
    style: 'unit',
    unit,
    unitDisplay: 'narrow',
    maximumFractionDigits: 1,
  }).format(amount);
}
