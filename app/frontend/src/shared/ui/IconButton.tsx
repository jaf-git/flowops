import type { ButtonHTMLAttributes, JSX } from 'react';

import { Icon, type IconName } from './Icon';

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  icon: IconName;

  label: string;
  variant?: 'quiet' | 'danger';
}

export function IconButton({
  icon,
  label,
  variant = 'quiet',
  ...rest
}: IconButtonProps): JSX.Element {
  return (
    <button
      {...rest}
      type={rest.type ?? 'button'}
      className={variant === 'danger' ? 'ui-icon-button ui-icon-button-danger' : 'ui-icon-button'}
      aria-label={label}
      title={label}
    >
      <Icon name={icon} size={20} />
    </button>
  );
}
