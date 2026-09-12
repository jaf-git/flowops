import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

interface ArchiveRunControlProps {
  finished: boolean;
  busy: boolean;
  onArchive: () => void;
}

export function ArchiveRunControl({
  finished,
  busy,
  onArchive,
}: ArchiveRunControlProps): JSX.Element | null {
  const { t } = useTranslation();

  if (!finished) {
    return null;
  }

  return (
    <button
      type="button"
      onClick={onArchive}
      disabled={busy}
      aria-label={t('canvas.group.archiveLabel')}
      style={{
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius-sm)',
        background: 'transparent',
        color: 'var(--muted)',
        padding: '2px var(--space-2)',
        fontSize: 'var(--text-xs)',
        cursor: busy ? 'progress' : 'pointer',
      }}
    >
      {t('canvas.group.archive')}
    </button>
  );
}
