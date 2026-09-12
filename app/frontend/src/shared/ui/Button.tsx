import type { ButtonHTMLAttributes, JSX, ReactNode } from 'react';

type Variant = 'primary' | 'accent' | 'secondary' | 'quiet' | 'tertiary' | 'destructive';

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  variant?: Variant;

  loading?: boolean;
  loadingLabel?: string;
  children: ReactNode;
}

export function Button({
  variant = 'primary',
  loading = false,
  loadingLabel,
  disabled,
  children,
  ...rest
}: ButtonProps): JSX.Element {
  const inert = disabled === true || loading;

  return (
    <button
      {...rest}

      type={rest.type ?? 'button'}
      className={`ui-button ui-button-${variant}`}
      disabled={inert}
      aria-busy={loading || undefined}
    >
      {loading && loadingLabel !== undefined ? loadingLabel : children}
    </button>
  );
}
