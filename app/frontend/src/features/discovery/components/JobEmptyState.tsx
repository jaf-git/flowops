import type { JSX } from 'react';

import type { JobHeader } from '../api/jobGraphApi';

interface JobEmptyStateProps {
  readonly header: JobHeader;

  readonly onOpenConversation?: () => void;
}

export function JobEmptyState({ header, onOpenConversation }: JobEmptyStateProps): JSX.Element {
  return (
    <div className="fo-jobempty">
      <p className="fo-jobempty-eyebrow">
        {header.client ?? 'Internal'}
        <span aria-hidden="true"> ▸ </span>
        {header.project ?? 'Unnamed project'}
      </p>

      <h3 className="fo-jobempty-title">Nothing has been marked yet</h3>

      <p className="fo-jobempty-body">
        This engagement exists and its boundary is drawn. Work appears here the moment somebody
        marks a message as theirs — one tap on the circle beside it, and the graph grows a piece of
        work.
      </p>

      <p className="fo-jobempty-body">
        Nobody has to decide anything first. The mark states where it will land before it commits,
        and joining work that already exists is the ordinary case.
      </p>

      {onOpenConversation === undefined ? null : (
        <button type="button" className="ui-button ui-button-accent" onClick={onOpenConversation}>
          Open the conversation
        </button>
      )}
    </div>
  );
}
