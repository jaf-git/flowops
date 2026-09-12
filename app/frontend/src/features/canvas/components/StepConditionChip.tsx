import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Chip } from '../../../shared/ui/Chip';
import type { StepCondition } from '../model/node';

const TONE: Record<StepCondition, 'brand' | 'done' | 'waiting' | 'neutral'> = {
  Pending: 'neutral',
  Reachable: 'waiting',
  Assigned: 'brand',
  Closed: 'neutral',
};

interface StepConditionChipProps {
  condition: StepCondition;
}

export function StepConditionChip({ condition }: StepConditionChipProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <Chip tone={TONE[condition]} dot>
      {t(`canvas.condition.${condition}`)}
    </Chip>
  );
}
