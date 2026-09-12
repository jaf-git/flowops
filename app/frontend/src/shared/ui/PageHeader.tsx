import type { JSX, ReactNode } from 'react';

interface PageHeaderProps {
  title: string;

  subtitle?: string;

  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, actions }: PageHeaderProps): JSX.Element {
  return (
    <header className="fo-page-header">
      <div className="fo-page-header-text">
        <h2 className="fo-page-title">{title}</h2>
        {subtitle === undefined ? null : <p className="fo-page-subtitle">{subtitle}</p>}
      </div>
      {actions === undefined ? null : <div className="fo-page-actions">{actions}</div>}
    </header>
  );
}
