import type { JSX } from 'react';

interface MarkCircleProps {
  marked: boolean;

  expanded: boolean;

  label: string;
  onClick: () => void;
}

export function MarkCircle({ marked, expanded, label, onClick }: MarkCircleProps): JSX.Element {
  return (
    <button
      type="button"
      className={marked ? 'fo-disc-circle fo-disc-circle--marked' : 'fo-disc-circle'}
      aria-label={label}
      aria-pressed={marked}
      aria-expanded={expanded}
      onClick={onClick}
    >
      <span aria-hidden="true">{marked ? '✓' : ''}</span>
    </button>
  );
}
