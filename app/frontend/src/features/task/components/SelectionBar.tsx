import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { FEWEST_TASKS_IN_A_PROCESS, type TaskSelection } from '../hooks/useTaskSelection';

interface SelectionBarProps {
  selection: TaskSelection;

  onCreateProcess?: () => void;
}

export function SelectionBar({
  selection,
  onCreateProcess,
}: SelectionBarProps): JSX.Element | null {
  const { t } = useTranslation();

  if (selection.count === 0) {
    return null;
  }

  return (
    <div className="fo-selection-bar" role="status" aria-live="polite">
      <span className="fo-selection-count">
        {t('task.selection.count', { count: selection.count })}
      </span>

      {!selection.enough && (
        <span className="fo-selection-why">
          {t('task.selection.needMore', { count: FEWEST_TASKS_IN_A_PROCESS - selection.count })}
        </span>
      )}

      <span className="fo-selection-actions">
        <Button variant="quiet" onClick={selection.clear}>
          {t('task.selection.clear')}
        </Button>
        {onCreateProcess !== undefined && (
          <Button variant="primary" onClick={onCreateProcess} disabled={!selection.enough}>
            {t('task.selection.createProcess')}
          </Button>
        )}
      </span>
    </div>
  );
}
