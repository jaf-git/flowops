import type { InputHTMLAttributes, JSX } from 'react';

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> {
  id: string;

  invalid?: boolean;

  describedBy?: 'hint' | 'error';
}

export function Input({ id, invalid, describedBy, ...rest }: InputProps): JSX.Element {
  return (
    <input
      {...rest}
      id={id}
      className="ui-control"
      aria-invalid={invalid === true ? true : undefined}
      aria-describedby={describedBy === undefined ? undefined : `${id}-${describedBy}`}
    />
  );
}
