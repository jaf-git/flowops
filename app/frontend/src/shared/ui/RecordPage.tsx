import type { JSX, ReactNode } from 'react';

interface RecordPageProps {
  readonly header: ReactNode;

  readonly children: ReactNode;

  readonly facts?: ReactNode;
  readonly factsLabel?: string;
}

export function RecordPage({ header, children, facts, factsLabel }: RecordPageProps): JSX.Element {
  return (
    <div className="fo-record">
      <div className="fo-record-head">{header}</div>

      <div className="fo-record-body">
        <div className="fo-record-prose">{children}</div>

        {facts === undefined ? null : (
          <aside className="fo-record-facts" aria-label={factsLabel}>
            {facts}
          </aside>
        )}
      </div>
    </div>
  );
}
