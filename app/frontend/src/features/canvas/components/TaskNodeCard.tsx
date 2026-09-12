import type { JSX, KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { relativeLabel } from '../../../shared/lib/elapsed';
import { Chip } from '../../../shared/ui/Chip';
import { PersonChip } from '../../../shared/ui/PersonChip';
import { PhaseBreakdown } from '../../../shared/ui/PhaseBreakdown';
import { TaskStateChip } from '../../../shared/ui/TaskStateChip';
import { borderTreatmentOf, isOverdue, type BorderTreatment, type CanvasNode } from '../model/node';
import { StateGlyph } from './StateGlyph';
import { StepConditionChip } from './StepConditionChip';

const NODE_WIDTH = '228px';

const BORDER: Record<BorderTreatment, { color: string; style: 'solid' | 'dashed' }> = {
  overdue: { color: 'var(--alert-line)', style: 'solid' },
  blocked: { color: 'var(--waiting)', style: 'solid' },
  ready: { color: 'var(--waiting)', style: 'solid' },
  selected: { color: 'var(--brand)', style: 'solid' },
  pending: { color: 'var(--line)', style: 'dashed' },
  default: { color: 'var(--line-soft)', style: 'solid' },
};

export interface TaskNodeCardProps {
  node: CanvasNode;

  selected?: boolean;
  onSelect?: (id: string) => void;

  now?: Date;

  onAssign?: () => void;

  describedBy?: string;
}

export function TaskNodeCard({
  node,
  selected,
  onSelect,
  now,
  onAssign,
  describedBy,
}: TaskNodeCardProps): JSX.Element {
  const { t, i18n } = useTranslation();

  const reference = now ?? new Date();
  const pending = node.condition === 'Pending';

  const assignee = pending ? undefined : node.assignee;
  const phases = pending ? undefined : node.phases;
  const given = node.dueAt === undefined || node.dueAt === null ? null : new Date(node.dueAt);
  const deadline = pending ? null : given;

  const overdue = isOverdue(node, reference);

  const treatment = borderTreatmentOf(node, { overdue, selected });
  const border = BORDER[treatment];

  const select = (): void => onSelect?.(node.id);
  const selectOnKey = (event: KeyboardEvent<HTMLDivElement>): void => {
    if (event.target !== event.currentTarget) {
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      select();
    }
  };

  return (
    <div
      role="button"
      tabIndex={0}
      aria-describedby={describedBy}
      onClick={select}
      onKeyDown={selectOnKey}
      className={pending ? 'canvas-node canvas-node-pending' : 'canvas-node'}
      data-urgency={treatment}
      style={{
        width: NODE_WIDTH,

        background: pending ? 'var(--bg)' : 'var(--surface)',

        borderWidth: '1px',
        borderStyle: border.style,
        borderColor: border.color,
        borderRadius: 'var(--radius-node)',
        cursor: 'pointer',
        userSelect: 'none',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 'var(--space-2)',
          padding: 'var(--space-3) var(--space-3) var(--space-2)',
        }}
      >
        <StateGlyph condition={node.condition} taskState={node.taskState} />
        <span style={{ minWidth: 0, flex: 1 }}>
          {node.stepNumber !== undefined && (
            <span
              data-testid="canvas-node-step"
              className="canvas-node-eyebrow"
              style={{
                display: 'block',
                color: 'var(--faint)',
                marginBottom: 'var(--space-1)',
              }}
            >
              {t('canvas.node.step', { number: node.stepNumber })}
            </span>
          )}
          <span
            data-testid="canvas-node-title"
            style={{
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              fontSize: 'var(--text-sm)',
              fontWeight: 500,

              lineHeight: 1.4,
              color: pending ? 'var(--muted)' : 'var(--ink)',
            }}
            title={node.title}
          >
            {node.title}
          </span>
        </span>
      </div>

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 'var(--space-1)',
          padding: '0 var(--space-3) var(--space-3)',
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

      {(node.blockedReason ?? node.reworkNote) !== undefined && (
        <div
          data-testid={
            node.blockedReason !== undefined ? 'canvas-node-blocker' : 'canvas-node-note'
          }
          style={{
            margin: '0 var(--space-3) var(--space-3)',
            padding: 'var(--space-2)',
            borderRadius: 'var(--radius-tile)',
            background: 'var(--waiting-soft)',
            color: 'var(--waiting)',
            fontSize: 'var(--text-xs)',
            lineHeight: 1.4,
          }}
        >
          {node.blockedReason ?? node.reworkNote}
        </div>
      )}

      {(assignee !== undefined || deadline !== null || node.meta !== undefined) && (
        <div
          data-testid="canvas-node-meta"
          style={{
            padding: 'var(--space-2) var(--space-3)',
            borderTop: '1px solid var(--line-soft)',
          }}
        >
          {assignee !== undefined && (
            <div style={{ paddingBottom: 'var(--space-1)' }}>
              <PersonChip name={assignee.name} erased={assignee.erased} />
            </div>
          )}
          {deadline !== null && (
            <MetaRow
              label={t('canvas.node.due')}
              value={relativeLabel(deadline, reference, i18n.language)}
              tone={overdue ? 'alert' : node.atRisk === true ? 'warn' : undefined}
              testId="canvas-node-due"
            />
          )}
          {node.meta?.map((row) => (
            <MetaRow key={row.label} label={row.label} value={row.value} tone={row.tone} />
          ))}
        </div>
      )}

      {phases !== undefined && phases.length > 0 && (
        <div
          data-testid="canvas-node-phases"
          style={{
            padding: 'var(--space-2) var(--space-3) var(--space-3)',
            borderTop: '1px solid var(--line-soft)',
          }}
        >
          <PhaseBreakdown spans={phases} variant="bar" />
        </div>
      )}

      {node.offersAssignment === true && onAssign !== undefined && (
        <div
          style={{
            padding: 'var(--space-2) var(--space-3) var(--space-3)',
            borderTop: '1px solid var(--line-soft)',
          }}
        >
          <button
            type="button"
            className="ui-button ui-button-primary canvas-node-cta"
            onClick={(event) => {
              event.stopPropagation();
              onAssign();
            }}
          >
            {t('canvas.node.assign')}
          </button>
        </div>
      )}
    </div>
  );
}

interface MetaRowProps {
  label: string;
  value: string;
  tone?: 'alert' | 'warn';
  testId?: string;
}

function MetaRow({ label, value, tone, testId }: MetaRowProps): JSX.Element {
  return (
    <div
      data-testid="canvas-node-meta-row"
      style={{
        display: 'flex',
        alignItems: 'baseline',
        justifyContent: 'space-between',
        gap: 'var(--space-2)',
        padding: '3px 0',
      }}
    >
      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--faint)', whiteSpace: 'nowrap' }}>
        {label}
      </span>
      <span
        data-testid={testId}
        style={{
          fontSize: 'var(--text-xs)',
          fontWeight: 500,
          textAlign: 'right',
          color:
            tone === 'alert' ? 'var(--alert)' : tone === 'warn' ? 'var(--waiting)' : 'var(--slate)',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {value}
      </span>
    </div>
  );
}
