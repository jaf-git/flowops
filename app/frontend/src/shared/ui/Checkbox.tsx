import type { JSX } from 'react';

interface CheckboxProps {
  id: string;
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;

  hint?: string;
  disabled?: boolean;

  struck?: boolean;

  labelHidden?: boolean;
}

export function Checkbox({
  id,
  label,
  checked,
  onChange,
  hint,
  disabled = false,
  struck = false,
  labelHidden = false,
}: CheckboxProps): JSX.Element {
  return (
    <label className="ui-checkbox-row" htmlFor={id}>
      <input
        id={id}
        type="checkbox"
        className="ui-checkbox"
        checked={checked}
        disabled={disabled}
        aria-describedby={hint === undefined ? undefined : `${id}-hint`}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="ui-checkbox-text">
        <span className={labelClass(labelHidden, struck)}>{label}</span>
        {hint !== undefined && (
          <span id={`${id}-hint`} className="ui-checkbox-hint">
            {hint}
          </span>
        )}
      </span>
    </label>
  );
}

function labelClass(hidden: boolean, struck: boolean): string {
  if (hidden) {
    return 'fo-visually-hidden';
  }
  return struck ? 'ui-checkbox-label ui-checkbox-label-struck' : 'ui-checkbox-label';
}
