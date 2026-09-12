import type { JSX, ReactNode } from 'react';

interface FocusPanelProps {
  title: string;

  lead?: string;
  children: ReactNode;

  action?: ReactNode;
}

export function FocusPanel({ title, lead, children, action }: FocusPanelProps): JSX.Element {
  return (
    <section className="fo-focus" aria-label={title}>
      <header className="fo-focus-head">
        <div>
          <h3 className="fo-focus-title">{title}</h3>
          {lead === undefined ? null : <p className="fo-focus-lead">{lead}</p>}
        </div>
        {action === undefined ? null : <div className="fo-focus-action">{action}</div>}
      </header>
      <div className="fo-focus-body">{children}</div>
    </section>
  );
}
