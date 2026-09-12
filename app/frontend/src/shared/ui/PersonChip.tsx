import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { initialsOf } from '../lib/initials';

interface PersonChipProps {
  name: string | null | undefined;

  role?: string;

  erased?: boolean;
  className?: string;
}

export function PersonChip({ name, role, erased, className }: PersonChipProps): JSX.Element {
  const { t } = useTranslation();

  const anonymous = erased === true || name === null || name === undefined || name.trim() === '';
  const label = anonymous ? t('ui.personChip.formerMember') : name;

  return (
    <span
      data-anonymous={anonymous ? 'true' : undefined}
      className={className === undefined ? 'ui-person' : `ui-person ${className}`}
    >
      <span aria-hidden="true" className="ui-person-disc">
        {anonymous ? '' : initialsOf(label)}
      </span>

      <span className="ui-person-name">{label}</span>

      {role !== undefined && role !== '' && <span className="ui-person-role">{role}</span>}
    </span>
  );
}
