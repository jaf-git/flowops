import { useEffect, useId, useRef, useState, type JSX } from 'react';

export interface ChipOption {
  readonly id: string;
  readonly label: string;

  readonly detail?: string;
}

interface DropdownChipProps {
  readonly name: string;
  readonly options: readonly ChipOption[];
  readonly value: string;
  readonly onChange: (id: string) => void;
}

export function DropdownChip({ name, options, value, onChange }: DropdownChipProps): JSX.Element {
  const [open, setOpen] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const current = options.find((option) => option.id === value) ?? options[0];

  useEffect(() => {
    if (!open) {
      return;
    }

    const dismiss = (event: MouseEvent): void => {
      if (!container.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    const escape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        setOpen(false);
        container.current?.querySelector('button')?.focus();
      }
    };

    document.addEventListener('mousedown', dismiss);
    document.addEventListener('keydown', escape);

    return () => {
      document.removeEventListener('mousedown', dismiss);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  if (options.length < 2) {
    return (
      <span className="fo-chip fo-chip--static">
        <span className="fo-chip-name">{name}</span>
        <span className="fo-chip-value">{current?.label ?? ''}</span>
      </span>
    );
  }

  return (
    <div className="fo-chip-shell" ref={container}>
      <button
        type="button"
        className="fo-chip"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={open ? menuId : undefined}
        onClick={() => {
          setOpen((was) => !was);
        }}
      >
        <span className="fo-chip-name">{name}</span>
        <span className="fo-chip-value">{current?.label ?? ''}</span>
        {current?.detail === undefined ? null : (
          <span className="fo-chip-detail">{current.detail}</span>
        )}
        <span aria-hidden="true" className="fo-chip-caret">
          ⌄
        </span>
      </button>

      {!open ? null : (
        <ul className="fo-chip-menu" id={menuId} role="listbox" aria-label={name}>
          {options.map((option) => (
            <li key={option.id}>
              <button
                type="button"
                role="option"
                aria-selected={option.id === value}
                className="fo-chip-option"
                onClick={() => {
                  onChange(option.id);
                  setOpen(false);
                }}
              >
                <span>{option.label}</span>
                {option.detail === undefined ? null : (
                  <span className="fo-chip-detail">{option.detail}</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
