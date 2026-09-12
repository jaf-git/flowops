import type { JSX } from 'react';
import type { StepperSegment } from './stepperSegments';

interface StepperCellProps {
  segments: readonly StepperSegment[];

  summary: string;
}

export function StepperCell({ segments, summary }: StepperCellProps): JSX.Element | null {
  if (segments.length === 0) {
    return null;
  }

  return (
    <span className="ui-stepper" role="img" aria-label={summary}>
      {segments.map((segment, index) => (
        <span key={index} className="ui-stepper-node" aria-hidden="true">
          <span
            className="ui-stepper-dot"
            data-condition={segment.condition}
            title={segment.title}
          />
          {index < segments.length - 1 ? (
            <span
              className="ui-stepper-link"

              data-condition={segment.condition}
            />
          ) : null}
        </span>
      ))}
    </span>
  );
}
