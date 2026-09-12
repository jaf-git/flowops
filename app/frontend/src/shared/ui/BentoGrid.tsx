import type { CSSProperties, JSX, ReactNode } from 'react';

interface BentoGridProps {
  readonly children: ReactNode;
}

export function BentoGrid({ children }: BentoGridProps): JSX.Element {
  return <div className="fo-bento">{children}</div>;
}

interface BentoTileProps {
  readonly span?: 1 | 2 | 3 | 4 | 5 | 6;

  readonly tall?: boolean;
  readonly children: ReactNode;
}

export function BentoTile({ span = 3, tall = false, children }: BentoTileProps): JSX.Element {
  const style: CSSProperties = {
    gridColumn: `span ${String(span)}`,
    gridRow: tall ? 'span 2' : undefined,
    minWidth: 0,
  };

  return (
    <div className="fo-bento-tile" style={style}>
      {children}
    </div>
  );
}
