import type { JSX, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { PersonChip } from '../../../shared/ui/PersonChip';
import { PhaseBreakdown } from '../../../shared/ui/PhaseBreakdown';
import { TaskStateChip } from '../../../shared/ui/TaskStateChip';
import type { InstanceStepPayload } from '../model/fromInstance';
import { phasesOfStep, taskStateOfStep } from '../model/fromInstance';
import { StepConditionChip } from './StepConditionChip';

export interface StepInspectorProps {
  step: InstanceStepPayload;
  onClose: () => void;

  onAssign?: () => void;

  actions?: ReactNode;

  onOpenTask?: () => void;
}

export function StepInspector({
  step,
  onClose,
  onAssign,
  actions,
  onOpenTask,
}: StepInspectorProps): JSX.Element {
  const { t } = useTranslation();
  const phases = phasesOfStep(step);
  const state = taskStateOfStep(step);

  return (
    <aside
      data-testid="step-inspector"
      aria-label={step.title}
      style={{
        width: '320px',
        flex: 'none',
        height: '100%',
        overflowY: 'auto',
        background: 'var(--surface)',
        borderLeft: '1px solid var(--line)',
        padding: 'var(--space-5)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)' }}>
        <span className="fo-eyebrow" style={{ margin: 0 }}>
          {t('canvas.node.step', { number: step.position + 1 })}
        </span>
        <button type="button" className="ui-button ui-button-secondary" onClick={onClose}>
          {t('canvas.inspector.close')}
        </button>
      </div>

      <h2 style={{ margin: 'var(--space-2) 0 var(--space-4)', fontSize: 'var(--text-base)' }}>
        {step.title}
      </h2>

      <div style={{ marginBottom: 'var(--space-4)' }}>
        {state === undefined ? (
          <StepConditionChip
            condition={
              step.condition === 'PENDING'
                ? 'Pending'
                : step.condition === 'REACHABLE'
                  ? 'Reachable'
                  : step.condition === 'CLOSED'
                    ? 'Closed'
                    : 'Assigned'
            }
          />
        ) : (
          <TaskStateChip state={state} />
        )}
      </div>

      {step.assigneeId !== null && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <PersonChip name={step.assigneeName === '' ? null : step.assigneeName} />
        </div>
      )}

      {step.blockedReason !== null && (
        <p
          data-testid="inspector-blocker"
          style={{
            margin: '0 0 var(--space-4)',
            padding: 'var(--space-3)',
            borderRadius: 'var(--radius-control)',
            background: 'var(--waiting-soft)',
            color: 'var(--waiting)',
            fontSize: 'var(--text-sm)',
            lineHeight: 1.5,
          }}
        >
          {step.blockedReason}
        </p>
      )}

      {phases.length > 0 && (
        <div data-testid="inspector-phases" style={{ marginBottom: 'var(--space-4)' }}>
          <PhaseBreakdown spans={phases} variant="bar" />

          <p
            style={{
              margin: 'var(--space-2) 0 0',
              color: 'var(--faint)',
              fontSize: 'var(--text-xs)',
            }}
          >
            {t('canvas.inspector.phaseRule')}
          </p>
        </div>
      )}

      {onAssign !== undefined && step.condition === 'REACHABLE' && (
        <button
          type="button"
          className="ui-button ui-button-primary"
          style={{ width: '100%', marginBottom: 'var(--space-4)' }}
          onClick={onAssign}
        >
          {t('canvas.node.assign')}
        </button>
      )}

      {actions}

      {step.taskId !== null && onOpenTask !== undefined && (
        <button
          type="button"
          className="ui-button ui-button-primary"
          style={{ width: '100%', marginTop: 'var(--space-4)' }}
          onClick={onOpenTask}
        >
          {t('canvas.inspector.openTask')}
        </button>
      )}
    </aside>
  );
}
