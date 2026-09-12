import type { JSX, ReactNode } from 'react';

interface ControlBarProps {
  readonly title: string;

  readonly chips?: ReactNode;

  readonly status?: ReactNode;

  readonly action?: ReactNode;

  readonly notifications?: ReactNode;
}

export function ControlBar({
  title,
  chips,
  status,
  action,
  notifications,
}: ControlBarProps): JSX.Element {
  return (
    <div className="fo-controlbar">
      <h1 className="fo-controlbar-title">{title}</h1>

      {chips === undefined ? null : <div className="fo-controlbar-chips">{chips}</div>}

      <div className="fo-controlbar-end">
        {status}
        {action}

        {notifications === undefined ? null : (
          <div className="fo-controlbar-corner">{notifications}</div>
        )}
      </div>
    </div>
  );
}
