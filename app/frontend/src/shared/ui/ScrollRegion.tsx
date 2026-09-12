import type { CSSProperties, JSX, ReactNode } from 'react';

interface ScrollRegionProps {
  label: string;
  children: ReactNode;

  maxHeight?: number | string;
  style?: CSSProperties;
}

export function ScrollRegion({
  label,
  children,
  maxHeight = 240,
  style,
}: ScrollRegionProps): JSX.Element {
  return (
    <div
      className="ui-scroll"
      role="region"
      aria-label={label}
      tabIndex={0}
      style={{ maxHeight, overflowY: 'auto', ...style }}
    >
      {children}
    </div>
  );
}
