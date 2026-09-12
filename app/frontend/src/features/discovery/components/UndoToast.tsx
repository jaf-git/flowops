import { useEffect, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Toast } from '../../../shared/ui/Toast';

export const UNDO_WINDOW_MS = 10_000;

type UndoToastProps =
  | {
      state: 'COUNTING';
      onUndo: () => void;

      onCommit: () => void;
    }
  | {
      state: 'DONE' | 'REFUSED';
      onDismiss: () => void;
    };

export function UndoToast(props: UndoToastProps): JSX.Element {
  const { t } = useTranslation();
  const [seconds, setSeconds] = useState(Math.round(UNDO_WINDOW_MS / 1000));
  const counting = props.state === 'COUNTING';

  useEffect(() => {
    if (!counting) {
      return;
    }

    const tick = setInterval(() => {
      setSeconds((left) => (left > 0 ? left - 1 : 0));
    }, 1000);
    return () => clearInterval(tick);
  }, [counting]);

  if (props.state !== 'COUNTING') {
    return (
      <Toast onDismiss={props.onDismiss} dismissLabel={t('discovery.strip.close')}>
        {t(props.state === 'DONE' ? 'discovery.undo.done' : 'discovery.undo.failed')}
      </Toast>
    );
  }

  return (
    <Toast
      autoDismissAfterMs={UNDO_WINDOW_MS}
      onDismiss={props.onCommit}
      action={
        <button
          type="button"
          className="fo-disc-undo__action"
          aria-label={t('discovery.undo.label')}
          onClick={props.onUndo}
        >
          {t('discovery.undo.action')}
        </button>
      }
    >
      <span className="fo-disc-undo">{t('discovery.undo.line', { seconds })}</span>
    </Toast>
  );
}
