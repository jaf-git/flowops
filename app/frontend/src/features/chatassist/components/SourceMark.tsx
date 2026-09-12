import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { FieldSource } from '../api/chatAssistApi';

export function SourceMark({ source }: { source: FieldSource }): JSX.Element {
  const { t } = useTranslation();

  return (
    <span
      style={{
        fontSize: 'var(--text-xs)',
        color: 'var(--muted)',
        whiteSpace: 'nowrap',
      }}
    >
      {t(`chatAssist.source.${source}`)}
    </span>
  );
}
