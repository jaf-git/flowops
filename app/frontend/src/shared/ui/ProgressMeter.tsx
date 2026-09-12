import { type JSX } from 'react';

interface ProgressMeterProps {
  done: number;

  total: number;

  label: string;
}

export function ProgressMeter({ done, total, label }: ProgressMeterProps): JSX.Element | null {
  if (total <= 0) {
    return null;
  }

  const proportion = Math.min(1, Math.max(0, done / total));

  return (
    <div
      className="ui-meter"
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={total}
      aria-valuenow={done}
      aria-label={label}
    >
      <div className="ui-meter-fill" style={{ width: `${String(proportion * 100)}%` }} />
    </div>
  );
}
