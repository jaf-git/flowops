import type { JSX, ReactNode } from 'react';

interface FieldProps {
  id: string;
  label: string;

  hint?: string;
  error?: string;
  required?: boolean;
  children: ReactNode;
}

export function Field({ id, label, hint, error, required, children }: FieldProps): JSX.Element {
  return (
    <div className="ui-field">
      <div className="ui-field-label-row">
        <label className="ui-field-label" htmlFor={id}>
          {label}
        </label>
        {required === true && (
          <span aria-hidden="true" className="ui-field-required">
            *
          </span>
        )}
      </div>

      {children}

      {hint !== undefined && error === undefined && (
        <p id={`${id}-hint`} className="ui-field-hint">
          {hint}
        </p>
      )}

      {error !== undefined && (
        <p id={`${id}-error`} role="alert" className="ui-field-error">
          {error}
        </p>
      )}
    </div>
  );
}
