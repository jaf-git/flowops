import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import type { InstanceStep } from '../api/processApi';

interface OptionalStepQuestionProps {
  step: InstanceStep;
  onSkip: () => void;
  onAssign: () => void;
  busy: boolean;
}

export function OptionalStepQuestion({
  step,
  onSkip,
  onAssign,
  busy,
}: OptionalStepQuestionProps): JSX.Element | null {
  const { t } = useTranslation();

  if (!step.optional || step.condition !== 'REACHABLE') {
    return null;
  }

  return (
    <div className="fo-step-question">
      <p className="fo-step-question__ask">
        {step.conditionNote ?? t('process.optionalStep.doesThisApply')}
      </p>
      <div className="fo-step-question__answers">
        <Button variant="primary" onClick={onAssign} disabled={busy}>
          {t('process.optionalStep.yes')}
        </Button>
        <Button variant="quiet" onClick={onSkip} disabled={busy}>
          {t('process.optionalStep.skip')}
        </Button>
      </div>
    </div>
  );
}
