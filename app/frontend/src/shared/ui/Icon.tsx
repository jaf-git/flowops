import type { JSX } from 'react';

const PATHS = {
  check: 'M20 6L9 17l-5-5',
  clock: 'M12 22a10 10 0 100-20 10 10 0 000 20zM12 6v6l4 2',
  alert:
    'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
  arrow: 'M5 12h14M12 5l7 7-7 7',
  globe: 'M12 22a10 10 0 100-20 10 10 0 000 20zM2 12h20M12 2a15 15 0 010 20 15 15 0 010-20',
  person: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z',
  lock: 'M5 11h14v10H5zM8 11V7a4 4 0 018 0v4',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  close: 'M18 6L6 18M6 6l12 12',
  plus: 'M12 5v14M5 12h14',

  chevronUp: 'M18 15l-6-6-6 6',
  chevronDown: 'M6 9l6 6 6-6',

  chevronLeft: 'M15 18l-6-6 6-6',

  people:
    'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0' +
    ' 00-3-3.87M16 3.13a4 4 0 010 7.75',
  minus: 'M5 12h14',

  fit: 'M3 8V3h5M16 3h5v5M21 16v5h-5M8 21H3v-5',

  reset: 'M20 12a8 8 0 11-2.34-5.66M21 3v5h-5',

  copy: 'M9 9h10a2 2 0 012 2v10a2 2 0 01-2 2H9a2 2 0 01-2-2V11a2 2 0 012-2zM5 15H4a2 2 0 01-2-2V3a2 2 0 012-2h10a2 2 0 012 2v1',

  edit: 'M17 3a2.83 2.83 0 014 4L7.5 20.5 2 22l1.5-5.5L17 3z',

  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',

  bell: 'M18 8a6 6 0 00-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0',

  openRecord: 'M7 17L17 7M9 7h8v8',
} as const;

export type IconName = keyof typeof PATHS;

const SIZES = [16, 20, 24] as const;

interface IconProps {
  name: IconName;

  size?: number;

  label?: string;

  tone?: 'inactive' | 'active';

  strokeWidth?: number;
}

export function Icon({ name, size = 16, label, tone, strokeWidth = 2 }: IconProps): JSX.Element {
  const drawn = snap(size);

  return (
    <svg
      width={drawn}
      height={drawn}
      viewBox="0 0 24 24"
      fill="none"
      stroke={STROKE[tone ?? 'inherit']}
      strokeWidth={Math.max(strokeWidth, 2)}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={label === undefined ? undefined : 'img'}
      aria-label={label}
      aria-hidden={label === undefined ? true : undefined}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

const STROKE = {
  inactive: 'var(--ink-400)',
  active: 'var(--ink-900)',
  inherit: 'currentColor',
} as const;

function snap(size: number): number {
  return SIZES.reduce((best, rung) =>
    Math.abs(rung - size) <= Math.abs(best - size) ? rung : best,
  );
}
