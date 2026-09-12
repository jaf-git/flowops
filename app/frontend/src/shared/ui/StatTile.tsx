import type { JSX, ReactNode } from 'react';

interface StatTileProps {
  label: string;

  value: ReactNode;

  sub?: string;

  viz?: ReactNode;
}

export function StatTile({ label, value, sub, viz }: StatTileProps): JSX.Element {
  return (
    <div className="ui-card fo-stat">
      <div className="fo-stat-label">{label}</div>
      <div className="fo-stat-value">{value}</div>
      {sub === undefined ? null : <div className="fo-stat-sub">{sub}</div>}
      {viz}
    </div>
  );
}
