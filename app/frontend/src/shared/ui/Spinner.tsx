import type { JSX } from 'react';

interface SpinnerProps {
  label: string;

  labelHidden?: boolean;
}

export function Spinner({ label, labelHidden = false }: SpinnerProps): JSX.Element {
  return (
    <span role="status" className="ui-spinner-row">
      <span className="ui-spinner" aria-hidden="true" />
      {labelHidden ? <span className="sr-only">{label}</span> : label}
    </span>
  );
}
