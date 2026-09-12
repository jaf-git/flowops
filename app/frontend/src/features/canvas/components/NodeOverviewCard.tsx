import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { relativeLabel } from '../../../shared/lib/elapsed';
import { Chip } from '../../../shared/ui/Chip';
import { PersonChip } from '../../../shared/ui/PersonChip';
import { PhaseBreakdown } from '../../../shared/ui/PhaseBreakdown';
import { TaskStateChip } from '../../../shared/ui/TaskStateChip';
import { isOverdue, type CanvasNode } from '../model/node';
import { StepConditionChip } from './StepConditionChip';

export interface NodeOverviewCardProps {
  node: CanvasNode;

  now?: Date;

  id?: string;
}

export function NodeOverviewCard({ node, now, id }: NodeOverviewCardProps): JSX.Element {
  const { t, i18n } = useTranslation();

  const reference = now ?? new Date();
  const pending = node.condition === 'Pending';
  const overdue = isOverdue(node, reference);

  const assignee = pending ? undefined : node.assignee;
  const phases = pending ? undefined : node.phases;
  const due =
    pending || node.dueAt === undefined || node.dueAt === null ? null : new Date(node.dueAt);

  return (
    <div
      id={id}
      role="tooltip"
      data-testid="node-overview-card"
      style={{
        width: '260px',
        background: 'var(--surface)',
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius-card)',
        boxShadow: 'var(--shadow-lg)',
        padding: 'var(--space-4)',
        pointerEvents: 'none',
      }}
    >
      {node.stepNumber !== undefined && (
        <p
          data-testid="overview-step"
          className="canvas-node-eyebrow"
          style={{ margin: '0 0 var(--space-1)', color: 'var(--faint)' }}
        >
          {t('canvas.node.step', { number: node.stepNumber })}
        </p>
      )}

      <p
        data-testid="overview-title"
        style={{
          margin: '0 0 var(--space-3)',
          fontSize: 'var(--text-sm)',
          fontWeight: 500,
          lineHeight: 1.4,
        }}
      >
        {node.title}
      </p>

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 'var(--space-1)',
          marginBottom: 'var(--space-3)',
        }}
      >
        {node.taskState === undefined ? (
          <StepConditionChip condition={node.condition} />
        ) : (
          <TaskStateChip state={node.taskState} />
        )}
        {node.atRisk === true && !overdue && <Chip tone="at-risk">{t('canvas.node.atRisk')}</Chip>}
        {overdue && <Chip tone="waiting">{t('canvas.node.overdue')}</Chip>}
      </div>

      {assignee !== undefined && (
        <div style={{ marginBottom: 'var(--space-3)' }}>
          <PersonChip name={assignee.name} erased={assignee.erased} />
        </div>
      )}

      {due !== null && (
        <p
          data-testid="overview-due"
          style={{
            margin: '0 0 var(--space-3)',
            fontSize: 'var(--text-xs)',
            color: overdue
              ? 'var(--alert)'
              : node.atRisk === true
                ? 'var(--waiting)'
                : 'var(--slate)',
          }}
        >
          {t('canvas.node.due')}: {relativeLabel(due, reference, i18n.language)}
        </p>
      )}

      {(node.blockedReason ?? node.reworkNote) !== undefined && (
        <p
          data-testid="overview-blocker"
          style={{
            margin: '0 0 var(--space-3)',
            padding: 'var(--space-2)',
            borderRadius: 'var(--radius-control)',
            background: 'var(--waiting-soft)',
            color: 'var(--waiting)',
            fontSize: 'var(--text-xs)',
            lineHeight: 1.5,
          }}
        >
          {node.blockedReason ?? node.reworkNote}
        </p>
      )}

      {node.meta?.map((row) => (
        <p
          key={row.label}
          data-testid="overview-meta"
          style={{
            margin: '0 0 var(--space-1)',
            fontSize: 'var(--text-xs)',
            color: 'var(--faint)',
          }}
        >
          {row.label}: <span style={{ color: 'var(--slate)' }}>{row.value}</span>
        </p>
      ))}

      {phases !== undefined && phases.length > 0 && (
        <div data-testid="overview-phases" style={{ marginTop: 'var(--space-3)' }}>
          <PhaseBreakdown spans={phases} variant="bar" />
        </div>
      )}
    </div>
  );
}
