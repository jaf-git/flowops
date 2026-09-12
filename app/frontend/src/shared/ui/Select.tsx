import type { JSX, SelectHTMLAttributes } from 'react';

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> {
  id: string;

  options: readonly (string | SelectOption)[];
  invalid?: boolean;
  describedBy?: 'hint' | 'error';
}

export function Select({ id, options, invalid, describedBy, ...rest }: SelectProps): JSX.Element {
  return (
    <select
      {...rest}
      id={id}
      className="ui-control"
      aria-invalid={invalid === true ? true : undefined}
      aria-describedby={describedBy === undefined ? undefined : `${id}-${describedBy}`}
    >
      {options.map((option) => {
        const { value, label } =
          typeof option === 'string' ? { value: option, label: option } : option;
        return (
          <option key={value} value={value}>
            {label}
          </option>
        );
      })}
    </select>
  );
}
