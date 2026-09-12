import { useEffect, useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { Chip } from '../../../shared/ui/Chip';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { IconButton } from '../../../shared/ui/IconButton';
import { ProgressMeter } from '../../../shared/ui/ProgressMeter';
import { Spinner } from '../../../shared/ui/Spinner';
import { Tabs } from '../../../shared/ui/Tabs';
import type { Candidate, Instance, InstanceStep, InstanceSummary } from '../api/processApi';
import { AbandonRunDialog } from '../components/AbandonRunDialog';
import { AddTaskToProcessDialog } from '../components/AddTaskToProcessDialog';
import { AssignStepDialog } from '../components/AssignStepDialog';
import { RemoveTaskFromProcessDialog } from '../components/RemoveTaskFromProcessDialog';
import { StartProcessFromTasksDialog } from '../components/StartProcessFromTasksDialog';
import { useInstance, useInstances, useReorderInstanceTasks } from '../hooks/useProcesses';

import { TemplateScreen } from './TemplateScreen';
import { StepperCell } from '../../../shared/ui/StepperCell';
import { segmentsFromProgress } from '../../../shared/ui/stepperSegments';
import { PageHeader } from '../../../shared/ui/PageHeader';
import { relativeLabel } from '../../../shared/lib/elapsed';

interface ProcessScreenProps {
  permissions: readonly string[];

  steerers: readonly Candidate[];

  viewerId: string;

  insightsFor?: (templateId: string) => ReactNode;
}

export function ProcessScreen({
  permissions,
  viewerId,
  steerers,
  insightsFor,
}: ProcessScreenProps): JSX.Element {
  const { t } = useTranslation();
  const [tab, setTab] = useState<'runs' | 'templates'>('runs');

  const [selected, setSelected] = useState<string | null>(null);

  return (
    <section aria-label={t('process.section.heading')} className="fo-page">
      <PageHeader title={t('process.section.heading')} subtitle={t('process.section.subtitle')} />
      <Tabs
        label={t('process.section.view')}
        active={tab}
        onSelect={(id) => {
          setTab(id === 'templates' ? 'templates' : 'runs');
        }}
        tabs={[
          { id: 'runs', label: t('process.section.runs') },
          { id: 'templates', label: t('process.section.templates') },
        ]}
      />
      {tab === 'runs' ? (
        <RunsPanel
          key={selected ?? 'none'}
          permissions={permissions}
          viewerId={viewerId}
          steerers={steerers}
          selected={selected}
          onSelect={setSelected}
        />
      ) : (
        <TemplateScreen
          permissions={permissions}
          viewerId={viewerId}
          steerers={steerers}
          insightsFor={insightsFor}

          onStarted={(started) => {
            setSelected(started.id);
            setTab('runs');
          }}
        />
      )}
    </section>
  );
}

function RunsPanel({
  permissions,
  viewerId,
  steerers,
  selected,
  onSelect,
}: {
  permissions: readonly string[];
  viewerId: string;
  steerers: readonly Candidate[];
  selected: string | null;
  onSelect: (id: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  const [assigning, setAssigning] = useState<InstanceStep | null>(null);
  const [starting, setStarting] = useState(false);
  const runs = useInstances();
  const run = useInstance(selected);

  const firstRun = runs.data?.instances[0]?.id;
  useEffect(() => {
    if (selected === null && firstRun !== undefined) {
      onSelect(firstRun);
    }
  }, [selected, firstRun, onSelect]);

  const mayStart = permissions.includes('PROCESS_INSTANTIATE');

  const newProcess = mayStart ? (
    <>
      <Button
        onClick={() => {
          setStarting(true);
        }}
      >
        {t('process.startFromTasks.open')}
      </Button>
      <StartProcessFromTasksDialog
        open={starting}
        steerers={steerers}
        onClose={() => {
          setStarting(false);
        }}
        onStarted={(started) => {
          onSelect(started.id);
        }}
      />
    </>
  ) : null;

  if (runs.isPending) {
    return <Spinner label={t('process.loading')} />;
  }

  if (runs.isError) {
    return <Banner tone="alert">{t('process.loadFailed')}</Banner>;
  }

  const instances = runs.data?.instances ?? [];

  if (instances.length === 0 && selected === null) {
    return (
      <EmptyState
        heading={t('process.emptyRuns.heading')}
        body={mayStart ? t('process.emptyRuns.body') : t('process.empty.body')}
        action={newProcess}
      />
    );
  }

  return (
    <div className="fo-page">
      {newProcess === null ? null : (
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>{newProcess}</div>
      )}

      <div className="fo-page-record">
        <RunList runs={instances} selected={selected} onSelect={onSelect} />
        {run.isError ? <Banner tone="alert">{t('process.loadFailed')}</Banner> : null}

        {selected !== null && run.isPending ? <Spinner label={t('process.loading')} /> : null}
        {run.data ? (
          <RunDetail
            run={run.data}
            permissions={permissions}
            viewerId={viewerId}
            maySteer={maySteer(permissions, viewerId, run.data)}
            onAssign={setAssigning}
          />
        ) : null}
      </div>
      {run.data ? (
        <AssignStepDialog
          instanceId={run.data.id}
          step={assigning}
          onClose={() => {
            setAssigning(null);
          }}
          onAssigned={() => {
            setAssigning(null);
          }}
        />
      ) : null}
    </div>
  );
}

function maySteer(permissions: readonly string[], viewerId: string, run: Instance): boolean {
  return permissions.includes('PROCESS_ASSIGN_STEP') || run.processOwnerId === viewerId;
}

function mayEditShape(permissions: readonly string[], viewerId: string, run: Instance): boolean {
  return permissions.includes('PROCESS_EDIT_INSTANCE') || run.processOwnerId === viewerId;
}

function RunList({
  runs,
  selected,
  onSelect,
}: {
  runs: InstanceSummary[];
  selected: string | null;
  onSelect: (id: string) => void;
}): JSX.Element {
  const { t } = useTranslation();
  return (
    <ul
      aria-label={t('process.section.runs')}
      style={{
        listStyle: 'none',
        margin: 0,
        padding: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)',
      }}
    >
      {runs.map((run) => (
        <li key={run.id}>
          <button
            type="button"
            aria-current={run.id === selected}
            onClick={() => {
              onSelect(run.id);
            }}
            style={{
              width: '100%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'flex-start',
              gap: 'var(--space-1)',
              padding: 'var(--space-3) var(--space-4)',
              borderRadius: 'var(--radius-card)',
              border: `1px solid ${run.id === selected ? 'var(--brand)' : 'var(--line)'}`,
              background: run.id === selected ? 'var(--brand-soft)' : 'var(--surface)',
              color: 'var(--ink)',
              textAlign: 'start',
              cursor: 'pointer',
            }}
          >
            {run.templateName === null ? null : (
              <span className="fo-run-process" data-testid="process-of-run">
                {run.templateName}
              </span>
            )}
            <span style={{ fontWeight: 600, fontSize: 'var(--text-base)' }}>{run.name}</span>
            <span style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
              {t(`process.state.${run.state}`)}
            </span>

            <span
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 'var(--space-2)',
                color: 'var(--muted)',
                fontSize: 'var(--text-sm)',
              }}
            >
              <StepperCell
                segments={segmentsFromProgress(run.progress.closed, run.progress.total)}
                summary={t('process.progress.label', {
                  closed: run.progress.closed,
                  total: run.progress.total,
                })}
              />
              {t('process.progress.label', {
                closed: run.progress.closed,
                total: run.progress.total,
              })}
            </span>
            {run.awaitingAssignmentCount > 0 ? (
              <span
                style={{
                  color: 'var(--brand-dark)',
                  fontSize: 'var(--text-sm)',
                  fontWeight: 600,
                }}
              >
                {t('process.awaiting.ready', { count: run.awaitingAssignmentCount })}
              </span>
            ) : null}
          </button>
        </li>
      ))}
    </ul>
  );
}

function RunDetail({
  run,
  permissions,
  viewerId,
  maySteer: mayAssign,
  onAssign,
}: {
  run: Instance;
  permissions: readonly string[];
  viewerId: string;
  maySteer: boolean;
  onAssign: (step: InstanceStep) => void;
}): JSX.Element {
  const { t, i18n } = useTranslation();
  const awaiting = run.steps.filter((step) => run.awaitingAssignment.includes(step.id));

  const mayEdit = mayEditShape(permissions, viewerId, run);
  const [adding, setAdding] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [removing, setRemoving] = useState<InstanceStep | null>(null);
  const reorder = useReorderInstanceTasks(run.id);

  const reorderCode = reorder.error instanceof ApiError ? reorder.error.code : undefined;

  const waitedHours = run.bottleneck === null ? 0 : Math.round(run.bottleneck.waitedMinutes / 60);
  const bottleneck = waitedHours >= 1 ? run.bottleneck : null;

  return (
    <Card>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--space-4)',
          flexWrap: 'wrap',
        }}
      >
        <div>
          {run.templateName === null ? null : (
            <p className="fo-run-process" data-testid="process-of-run" style={{ margin: 0 }}>
              {run.templateName}
            </p>
          )}
          <h2
            style={{
              margin: 0,
              fontSize: 'var(--text-lg)',
              fontWeight: 600,
              letterSpacing: '-0.01em',
            }}
          >
            {run.name}
          </h2>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
          <Link
            className="ui-button ui-button-secondary"
            to={`/${i18n.language}/canvas/process/${run.id}`}
          >
            {t('process.canvas.open')}
          </Link>

          {permissions.includes('PROCESS_ABANDON_INSTANCE') && run.state === 'RUNNING' ? (
            <Button
              variant="quiet"
              onClick={() => {
                setStopping(true);
              }}
            >
              {t('process.abandon.open')}
            </Button>
          ) : null}
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-3)',
          flexWrap: 'wrap',
        }}
      >
        <Chip
          tone={run.state === 'RUNNING' ? 'brand' : run.state === 'ABANDONED' ? 'blocked' : 'done'}
          dot
        >
          {t(`process.state.${run.state}`)}
        </Chip>
        <span style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
          {t('process.progress.label', {
            closed: run.progress.closed,
            total: run.progress.total,
          })}
        </span>
      </div>

      {run.state === 'ABANDONED' && run.abandonedReason !== null ? (
        <Banner tone="waiting">
          {t('process.abandon.stopped', { reason: run.abandonedReason })}
          {run.needingAttention.length > 0
            ? ` ${t('process.abandon.stillHeld', { count: run.needingAttention.length })}`
            : ''}
        </Banner>
      ) : null}

      <ProgressMeter
        done={run.progress.closed}
        total={run.progress.total}
        label={t('process.progress.label', {
          closed: run.progress.closed,
          total: run.progress.total,
        })}
      />

      {awaiting.length > 0 ? (
        <section
          aria-label={t('process.awaiting.heading')}
          style={{ display: 'grid', gap: 'var(--space-2)' }}
        >
          <h3 className="fo-eyebrow">{t('process.awaiting.heading')}</h3>
          <p style={{ margin: 0, color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
            {t('process.awaiting.ready', { count: awaiting.length })}
          </p>
          <ul
            style={{
              listStyle: 'none',
              margin: 0,
              padding: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--space-2)',
            }}
          >
            {awaiting.map((step) => (
              <li
                key={step.id}
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 'var(--space-3)',
                  padding: 'var(--space-3) var(--space-4)',
                  borderRadius: 'var(--radius-card)',
                  background: 'var(--brand-soft)',
                  border: '1px solid var(--brand-line)',
                }}
              >
                <span style={{ fontWeight: 600, color: 'var(--ink)' }}>{step.title}</span>

                {mayAssign ? (
                  <Button
                    onClick={() => {
                      onAssign(step);
                    }}
                  >
                    {t('process.awaiting.assign')}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {bottleneck ? (
        <p
          style={{
            margin: 0,
            padding: 'var(--space-3) var(--space-4)',
            borderRadius: 'var(--radius-control)',
            background: 'var(--waiting-soft)',
            color: 'var(--waiting)',
            fontSize: 'var(--text-md)',
            lineHeight: 1.6,
          }}
        >
          {t('process.bottleneck.label')}:{' '}
          {run.steps.find((step) => step.id === bottleneck.stepId)?.title}{' '}
          {t('process.bottleneck.waiting', { count: waitedHours })}
        </p>
      ) : null}

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 'var(--space-3)',
          margin: 'var(--space-4) 0 0',
          flexWrap: 'wrap',
        }}
      >
        <h3 className="fo-eyebrow">{t('process.section.steps')}</h3>

        {mayEdit && run.state === 'RUNNING' ? (
          <Button
            onClick={() => {
              setAdding(true);
            }}
          >
            {t('process.addTask.open')}
          </Button>
        ) : null}
      </div>

      {reorderCode !== undefined ? (
        <Banner tone="alert">
          {t(`process.reorder.error.${reorderCode}`, t('process.reorder.error.UNKNOWN'))}
        </Banner>
      ) : null}

      <ol
        aria-label={t('process.section.steps')}
        style={{
          listStyle: 'none',
          margin: 'var(--space-2) 0 0',
          padding: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-2)',
        }}
      >
        {run.steps.map((step, position) => (
          <StepRow
            key={step.id}
            step={step}
            run={run}
            mayEdit={mayEdit && run.state === 'RUNNING'}
            position={position}

            moving={reorder.isPending}
            onMove={(to) => {
              reorder.mutate(orderWithMoved(run, position, to));
            }}
            onRemove={() => {
              setRemoving(step);
            }}
          />
        ))}
      </ol>

      {stopping ? (
        <AbandonRunDialog
          instanceId={run.id}
          name={run.name}

          inFlight={
            run.steps.filter(
              (step) =>
                step.taskId !== null &&
                step.taskState !== 'CLOSED' &&
                step.taskState !== 'APPROVED',
            ).length
          }
          onClose={() => {
            setStopping(false);
          }}
          onAbandoned={() => {
            setStopping(false);
          }}
        />
      ) : null}

      <AddTaskToProcessDialog
        open={adding}
        instance={run}
        onClose={() => {
          setAdding(false);
        }}
        onAdded={() => {
          setAdding(false);
        }}
      />
      <RemoveTaskFromProcessDialog
        open={removing !== null}
        instance={run}
        step={removing}
        onClose={() => {
          setRemoving(null);
        }}
      />
    </Card>
  );
}

function orderWithMoved(run: Instance, from: number, to: number): string[] {
  const ids = run.steps.map((step) => step.id);
  const moved = ids[from];
  if (moved === undefined) {
    return ids;
  }
  ids.splice(from, 1);
  ids.splice(to, 0, moved);
  return ids;
}

function StepRow({
  step,
  run,
  mayEdit,
  position,
  moving,
  onMove,
  onRemove,
}: {
  step: InstanceStep;
  run: Instance;
  mayEdit: boolean;
  position: number;
  moving: boolean;
  onMove: (to: number) => void;
  onRemove: () => void;
}): JSX.Element {
  const { t, i18n } = useTranslation();
  const waitsFor = step.dependsOn
    .map((id) => run.steps.find((each) => each.id === id)?.title)
    .filter((title): title is string => title !== undefined);

  return (
    <li
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 'var(--space-3)',
        padding: 'var(--space-3) var(--space-4)',
        borderRadius: 'var(--radius-card)',
        background: 'var(--surface)',
        border: '1px solid var(--line)',
      }}
    >
      <span style={{ fontWeight: 600, color: 'var(--ink)', fontSize: 'var(--text-base)' }}>
        {step.title}
      </span>

      <Chip tone="neutral">
        {step.condition === 'ASSIGNED' && step.assigneeName !== null && step.assigneeName !== ''
          ? t('process.condition.ASSIGNED_TO', { name: step.assigneeName })
          : t(`process.condition.${step.condition}`)}
      </Chip>

      {step.deadline === null || step.deadline === undefined ? null : (
        <span
          style={{
            color: step.atRisk ? 'var(--waiting)' : 'var(--muted)',
            fontSize: 'var(--text-sm)',
          }}
        >
          {t('process.step.due', {
            when: relativeLabel(new Date(step.deadline), new Date(), i18n.language),
          })}
        </span>
      )}

      {waitsFor.length > 0 ? (
        <span style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
          {t('process.step.waitsFor')}: {waitsFor.join(', ')}
        </span>
      ) : (
        <span style={{ color: 'var(--muted)', fontSize: 'var(--text-sm)' }}>
          {t('process.step.waitsForNothing')}
        </span>
      )}
      {step.blockedReason !== null && step.blockedReason !== undefined ? (
        <span style={{ color: 'var(--alert)', fontSize: 'var(--text-sm)', fontWeight: 500 }}>
          {t('process.step.blocked', { reason: step.blockedReason })}
        </span>
      ) : null}

      {mayEdit ? (
        <span style={{ marginLeft: 'auto', display: 'flex', gap: 'var(--space-2)' }}>
          <IconButton
            icon="chevronUp"
            label={t('process.reorder.up', { title: step.title })}
            disabled={moving || position === 0}
            onClick={() => {
              onMove(position - 1);
            }}
          />
          <IconButton
            icon="chevronDown"
            label={t('process.reorder.down', { title: step.title })}
            disabled={moving || position === run.steps.length - 1}
            onClick={() => {
              onMove(position + 1);
            }}
          />
          <IconButton
            icon="close"
            label={t('process.removeTask.open', { title: step.title })}
            onClick={onRemove}
          />
        </span>
      ) : null}
    </li>
  );
}
