import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Chip } from './Chip';

export type TaskState =
  'Created' | 'Accepted' | 'InProgress' | 'Blocked' | 'Completed' | 'Approved' | 'Closed';

const TONE: Record<TaskState, 'brand' | 'done' | 'waiting' | 'neutral' | 'blocked'> = {
  Created: 'neutral',
  Accepted: 'neutral',
  InProgress: 'brand',
  Blocked: 'blocked',
  Completed: 'waiting',
  Approved: 'done',
  Closed: 'neutral',
};

const GLYPH: Record<TaskState, string> = {
  Created: '◇',
  Accepted: '◇',
  InProgress: '◆',
  Blocked: '▲',
  Completed: '●',
  Approved: '✓',
  Closed: '—',
};

interface TaskStateChipProps {
  state: TaskState;
}

export function TaskStateChip({ state }: TaskStateChipProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <Chip tone={TONE[state]}>
      <span aria-hidden="true" className="ui-chip-state-glyph">
        {GLYPH[state]}
      </span>
      {t(`ui.taskState.${state}`)}
    </Chip>
  );
}
