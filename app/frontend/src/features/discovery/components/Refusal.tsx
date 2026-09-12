import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';

interface RefusalProps {
  code: string | undefined;
}

export function Refusal({ code }: RefusalProps): JSX.Element | null {
  const { t } = useTranslation();

  if (code === undefined) {
    return null;
  }

  return (
    <Banner tone="alert">
      {t(`discovery.error.${code}`, { defaultValue: t('discovery.error.UNKNOWN') })}
    </Banner>
  );
}
