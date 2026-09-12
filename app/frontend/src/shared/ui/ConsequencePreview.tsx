import { useId } from 'react';
import type { JSX } from 'react';

interface ConsequencePreviewProps {
  heading: string;

  consequences: readonly string[];
  className?: string;
}

export function ConsequencePreview({
  heading,
  consequences,
  className,
}: ConsequencePreviewProps): JSX.Element | null {
  const headingId = useId();

  if (consequences.length === 0) {
    return null;
  }

  return (
    <div className={className === undefined ? 'ui-consequence' : `ui-consequence ${className}`}>
      <p id={headingId} className="ui-consequence-heading">
        {heading}
      </p>

      <ul aria-labelledby={headingId} className="ui-consequence-list">
        {consequences.map((consequence) => (
          <li key={consequence}>{consequence}</li>
        ))}
      </ul>
    </div>
  );
}
