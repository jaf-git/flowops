import { useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { ConnectionIndicator } from '../../../shared/realtime/ConnectionIndicator';
import { Banner } from '../../../shared/ui/Banner';
import type { TaskLifecycleState } from '../../../shared/ui/TaskActionRail';
import { ProcessGraphCanvas } from '../components/ProcessGraphCanvas';
import { StepInspector } from '../components/StepInspector';
import { useLiveOperationsCanvas } from '../hooks/useLiveOperationsCanvas';
import { lifecycleStateOfStep } from '../model/fromInstance';

export interface StepTaskSubject {
  taskId: string;
  state: TaskLifecycleState;

  mine: boolean;

  hasDeadline: boolean;
  deadline: string | null;
}

export interface StepAssignSubject {
  instanceId: string;
  stepId: string;
  onClose: () => void;
  onAssigned: () => void;
}

interface ProcessCanvasScreenProps {
  instanceId: string;

  viewerId?: string;

  rail?: ReactNode;

  renderTaskActions?: (subject: StepTaskSubject) => ReactNode;

  renderAssign?: (subject: StepAssignSubject) => ReactNode;
}

function LoadingPlane({ label }: { label: string }): JSX.Element {
  const tiers = [1, 2, 2];

  return (
    <main
      aria-busy="true"
      aria-label={label}
      data-testid="operations-plane-skeleton"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-6)',
        height: '100vh',
        padding: 'var(--space-7) var(--space-6)',
      }}
    >
      {tiers.map((count, tier) => (
        <div
          key={tier}
          style={{ display: 'flex', gap: 'var(--space-5)', justifyContent: 'center' }}
        >
          {Array.from({ length: count }, (_, node) => (
            <div
              key={node}
              data-testid="node-skeleton"
              style={{
                width: '220px',
                height: '104px',

                borderRadius: 'var(--radius-card)',
                border: '1px solid var(--line)',
                background: 'var(--surface)',
                opacity: 0.55,
              }}
            />
          ))}
        </div>
      ))}
    </main>
  );
}

export function ProcessCanvasScreen({
  instanceId,
  viewerId,
  rail,
  renderTaskActions,
  renderAssign,
}: ProcessCanvasScreenProps): JSX.Element {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string | undefined>(undefined);
  const [assigning, setAssigning] = useState<string | undefined>(undefined);
  const { instance, status } = useLiveOperationsCanvas(instanceId);

  if (instance.isPending) {
    return <LoadingPlane label={t('canvas.instance.loading')} />;
  }

  if (instance.isError || instance.data === undefined) {
    return (
      <main style={{ padding: 'var(--space-6)' }}>
        <Banner tone="alert">{t('canvas.instance.unavailable')}</Banner>
      </main>
    );
  }

  const stale = status === 'reconnecting' || status === 'offline';

  const opened = instance.data.raw.find((step) => step.id === selected);
  const waiting = instance.data.steps.filter((step) => step.offersAssignment === true).length;
  const { closed, total } = instance.data.progress;

  const actionsFor = (step: typeof opened): ReactNode => {
    const state = step === undefined ? undefined : lifecycleStateOfStep(step);

    if (step === undefined || step.taskId === null || state === undefined) {
      return undefined;
    }

    return renderTaskActions?.({
      taskId: step.taskId,
      state,
      mine: step.assigneeId !== null && step.assigneeId === viewerId,
      hasDeadline: step.deadline !== null,
      deadline: step.deadline,
    });
  };

  const assigned = (): void => {
    setAssigning(undefined);
  };

  const assignment =
    assigning === undefined
      ? undefined
      : renderAssign?.({
          instanceId,
          stepId: assigning,
          onClose: () => setAssigning(undefined),
          onAssigned: assigned,
        });

  const plane = (
    <main style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <header
        style={{
          padding: 'var(--space-5) var(--space-6)',
          borderBottom: '1px solid var(--line)',
          background: 'var(--surface)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          <h1 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 600 }}>
            {instance.data.name}
          </h1>
          <ConnectionIndicator status={status} />
        </div>
        <p
          data-testid="canvas-stall-banner"
          style={{
            margin: 'var(--space-2) 0 0',
            fontSize: 'var(--text-sm)',
            fontWeight: 500,
            color: waiting > 0 ? 'var(--waiting)' : 'var(--muted)',
          }}
        >
          {waiting > 0
            ? t('canvas.instance.awaiting', { count: waiting })
            : t('canvas.instance.progress', { closed, total })}
        </p>
      </header>

      <div
        style={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          filter: stale ? 'saturate(0.62)' : undefined,
        }}
        data-stale={stale ? 'true' : undefined}
      >
        <div style={{ flex: 1, minWidth: 0 }} data-testid="operations-plane">
          <ProcessGraphCanvas
            steps={instance.data.steps}
            edges={instance.data.edges}
            selected={selected}
            onSelect={setSelected}
            onAssign={renderAssign === undefined ? undefined : setAssigning}
          />
        </div>

        {opened !== undefined && (
          <StepInspector
            step={opened}
            onClose={() => setSelected(undefined)}
            actions={actionsFor(opened)}
            onAssign={renderAssign === undefined ? undefined : () => setAssigning(opened.id)}
          />
        )}
      </div>

      {assignment}
    </main>
  );

  return rail === undefined ? (
    plane
  ) : (
    <div style={{ display: 'flex', height: '100vh' }}>
      {rail}
      <div style={{ flex: 1, minWidth: 0 }}>{plane}</div>
    </div>
  );
}
