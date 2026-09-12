import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';
import type { Instance } from '../api/processApi';
import { useInstance, useSkipStep } from '../hooks/useProcesses';
import { AssignStepDialog } from './AssignStepDialog';
import { OptionalStepQuestion } from './OptionalStepQuestion';

interface AssignStepFromInstanceProps {
  instanceId: string;
  stepId: string;
  onClose: () => void;
  onAssigned: (instance: Instance) => void;
}

export function AssignStepFromInstance({
  instanceId,
  stepId,
  onClose,
  onAssigned,
}: AssignStepFromInstanceProps): JSX.Element | null {
  const { t } = useTranslation();
  const [decided, setDecided] = useState(false);
  const instance = useInstance(instanceId);
  const skip = useSkipStep(instanceId);

  if (instance.isPending) {
    return null;
  }

  if (instance.isError || instance.data === undefined) {
    return <Banner tone="alert">{t('process.loadFailed')}</Banner>;
  }

  const step = instance.data.steps.find((candidate) => candidate.id === stepId);

  if (step === undefined) {
    return <Banner tone="alert">{t('process.assign.error.STEP_NOT_FOUND')}</Banner>;
  }

  if (step.optional && step.condition === 'REACHABLE' && !decided) {
    return (
      <OptionalStepQuestion
        step={step}
        busy={skip.isPending}
        onAssign={() => setDecided(true)}
        onSkip={() =>
          skip.mutate(
            { stepId },
            {
              onSuccess: (moved) => {
                onAssigned(moved);
                onClose();
              },
            },
          )
        }
      />
    );
  }

  return (
    <AssignStepDialog
      instanceId={instanceId}
      step={step}
      onClose={onClose}
      onAssigned={onAssigned}
    />
  );
}
