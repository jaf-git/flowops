import type { CSSProperties, JSX, ReactNode } from 'react';

interface GlyphTileProps {
  fill: string;

  ink: string;

  size?: number;
  children: ReactNode;
}

export function GlyphTile({ fill, ink, size = 32, children }: GlyphTileProps): JSX.Element {
  const style: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    flex: 'none',
    width: `${size}px`,
    height: `${size}px`,
    borderRadius: 'var(--radius-tile)',
    background: fill,
    color: ink,
    boxShadow: `inset 0 0 0 1px color-mix(in srgb, ${ink} 14%, transparent)`,
  };

  return (
    <span data-testid="canvas-glyph-tile" style={style}>
      {children}
    </span>
  );
}

export function TileGlyph({ size, children }: { size: number; children: ReactNode }): JSX.Element {
  return (
    <svg
      width={Math.round(size * 0.54)}
      height={Math.round(size * 0.54)}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}
