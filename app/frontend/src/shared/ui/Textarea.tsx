import type { JSX, TextareaHTMLAttributes } from 'react';

interface TextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'> {
  id: string;

  invalid?: boolean;

  describedBy?: 'hint' | 'error';

  rows?: number;
}

export function Textarea({
  id,
  invalid,
  describedBy,
  rows = 3,
  ...rest
}: TextareaProps): JSX.Element {
  return (
    <textarea
      {...rest}
      id={id}
      rows={rows}
      className="ui-control ui-control-multiline"
      aria-invalid={invalid === true ? true : undefined}
      aria-describedby={describedBy === undefined ? undefined : `${id}-${describedBy}`}
    />
  );
}
