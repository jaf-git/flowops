import type { JSX, ReactNode } from 'react';

import { Icon, type IconName } from './Icon';

interface EmptyStateProps {
  icon?: IconName;

  heading: string;

  body?: string;

  action?: ReactNode;
}

export function EmptyState({ icon, heading, body, action }: EmptyStateProps): JSX.Element {
  return (
    <div className="ui-empty">
      {icon !== undefined && (
        <span aria-hidden="true" className="ui-empty-glyph">
          <Icon name={icon} size={28} />
        </span>
      )}
      <p className="ui-empty-heading">{heading}</p>
      {body !== undefined && <p className="ui-empty-body">{body}</p>}
      {action === undefined ? null : <div className="ui-empty-action">{action}</div>}
    </div>
  );
}
