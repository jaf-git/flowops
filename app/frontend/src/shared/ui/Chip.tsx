import type { JSX, ReactNode } from 'react';

type Tone = 'brand' | 'done' | 'waiting' | 'neutral' | 'blocked' | 'at-risk';

interface ChipProps {
  tone?: Tone;

  dot?: boolean;
  children: ReactNode;
}

export function Chip({ tone = 'neutral', dot = false, children }: ChipProps): JSX.Element {
  return (
    <span className={`ui-chip ui-chip-${tone}`}>
      {dot && <span aria-hidden="true" className="ui-chip-dot" />}
      {children}
    </span>
  );
}
