import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { MOST_FOR_A_PROCESS, whyNotBuildable } from '../model/selection';

interface SelectionBarProps {
  chosen: readonly string[];
  busy: boolean;
  onBuild: () => void;
  onCancel: () => void;
}

export function SelectionBar({ chosen, busy, onBuild, onCancel }: SelectionBarProps): JSX.Element {
  const { t } = useTranslation();
  const refusal = whyNotBuildable(chosen);

  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-3)',
        padding: 'var(--space-3) var(--space-6)',
        borderBlockStart: '1px solid var(--line)',
        background: 'var(--brand-soft)',
      }}
    >
      <span style={{ fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--brand-dark)' }}>
        {t('chat.selection.chosen', { count: chosen.length })}
      </span>

      {refusal === 'TOO_MANY' ? (
        <span style={{ fontSize: 'var(--text-sm)', color: 'var(--alert)' }}>
          {t('chat.selection.tooMany', { count: MOST_FOR_A_PROCESS })}
        </span>
      ) : null}

      <span style={{ marginInlineStart: 'auto', display: 'flex', gap: 'var(--space-2)' }}>
        <button type="button" className="ui-button ui-button-quiet" onClick={onCancel}>
          {t('chat.selection.cancel')}
        </button>
        <button
          type="button"

          className="ui-button ui-button-primary"
          onClick={onBuild}
          disabled={refusal !== null || busy}
        >
          {t('chat.selection.build')}
        </button>
      </span>
    </div>
  );
}
