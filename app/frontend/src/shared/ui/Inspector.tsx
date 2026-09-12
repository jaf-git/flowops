import type { JSX, ReactNode } from 'react';

interface InspectorProps {
  readonly children?: ReactNode;
  readonly open: boolean;
  readonly onToggle: () => void;
  readonly label: string;

  readonly empty?: ReactNode;
}

export function Inspector({ children, open, onToggle, label, empty }: InspectorProps): JSX.Element {
  return (
    <aside
      className={open ? 'fo-inspector' : 'fo-inspector fo-inspector--closed'}
      aria-label={label}
    >
      <button
        type="button"
        className="fo-inspector-toggle"
        aria-expanded={open}
        aria-label={label}
        title={label}
        onClick={onToggle}
      >
        <span aria-hidden="true">{open ? '±' : '±'}</span>
      </button>

      <div className="fo-inspector-body" hidden={!open}>
        {children ?? empty}
      </div>
    </aside>
  );
}
