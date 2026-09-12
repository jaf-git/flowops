import type { CSSProperties, JSX } from 'react';

import { initialsOf } from '../lib/initials';

interface AvatarProps {
  name: string;

  id?: string;
  size?: number;
}

const DISCS = [
  'var(--person-1)',
  'var(--person-2)',
  'var(--person-3)',
  'var(--person-4)',
  'var(--person-5)',
  'var(--person-6)',
] as const;

export function Avatar({ name, id, size = 38 }: AvatarProps): JSX.Element {
  const initials = initialsOf(name);
  const tinted = id !== undefined && initials !== '';

  const style: CSSProperties = {
    width: `${String(size)}px`,
    height: `${String(size)}px`,
    flexShrink: 0,
    borderRadius: 'var(--radius-chip)',

    background: tinted ? discFor(id) : 'var(--surface-sunken)',
    color: tinted ? 'var(--on-ink)' : 'var(--ink-700)',
    display: 'grid',
    placeItems: 'center',

    fontWeight: 600,
    fontSize: `${String(Math.round(size * 0.37))}px`,
    userSelect: 'none',
  };

  return (
    <span aria-hidden="true" style={style}>
      {initials}
    </span>
  );
}

function discFor(id: string): string {
  let total = 0;
  for (const character of id) {
    total = (total + (character.codePointAt(0) ?? 0)) % 4096;
  }
  return DISCS[total % DISCS.length] as string;
}
