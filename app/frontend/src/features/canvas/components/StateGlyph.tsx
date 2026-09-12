import type { JSX, ReactNode } from 'react';

import type { TaskState } from '../../../shared/ui/TaskStateChip';
import type { StepCondition } from '../model/node';
import { CONDITION_TONE, STATE_TONE, type Tone } from '../model/tone';
import { GlyphTile, TileGlyph } from './GlyphTile';

const PAINT: Record<Tone, { fill: string; ink: string }> = {
  brand: { fill: 'var(--brand-soft)', ink: 'var(--brand-dark)' },
  done: { fill: 'var(--done-soft)', ink: 'var(--done)' },
  waiting: { fill: 'var(--waiting-soft)', ink: 'var(--waiting)' },
  blocked: { fill: 'var(--waiting-soft)', ink: 'var(--waiting)' },
  neutral: { fill: 'var(--line-soft)', ink: 'var(--muted)' },
};

const CLOCK = (
  <>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </>
);
const CHECK = <path d="M20 6 9 17l-5-5" />;
const PAUSE = (
  <>
    <path d="M10 5v14" />
    <path d="M14 5v14" />
  </>
);
const EYE = (
  <>
    <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" />
    <circle cx="12" cy="12" r="2.5" />
  </>
);
const LOCK = (
  <>
    <rect x="4" y="10" width="16" height="10" rx="2" />
    <path d="M8 10V7a4 4 0 0 1 8 0v3" />
  </>
);
const PERSON = (
  <>
    <circle cx="12" cy="8" r="3.5" />
    <path d="M5 20a7 7 0 0 1 14 0" />
  </>
);
const FLOW = (
  <>
    <rect x="3" y="4" width="7" height="6" rx="1.5" />
    <rect x="14" y="14" width="7" height="6" rx="1.5" />
    <path d="M6.5 10v4a3 3 0 0 0 3 3H14" />
  </>
);

const STATE_ICON: Record<TaskState, ReactNode> = {
  Created: CLOCK,
  Accepted: CHECK,
  InProgress: FLOW,
  Blocked: PAUSE,
  Completed: EYE,
  Approved: CHECK,
  Closed: CHECK,
};

const CONDITION_ICON: Record<StepCondition, ReactNode> = {
  Pending: LOCK,
  Reachable: PERSON,
  Assigned: FLOW,
  Closed: CHECK,
};

const TILE = 32;

interface StateGlyphProps {
  condition: StepCondition;
  taskState?: TaskState;
}

export function StateGlyph({ condition, taskState }: StateGlyphProps): JSX.Element {
  const tone = taskState === undefined ? CONDITION_TONE[condition] : STATE_TONE[taskState];
  const paint = PAINT[tone];

  return (
    <span data-testid="canvas-node-glyph" style={{ display: 'inline-flex' }}>
      <GlyphTile fill={paint.fill} ink={paint.ink} size={TILE}>
        <TileGlyph size={TILE}>
          {taskState === undefined ? CONDITION_ICON[condition] : STATE_ICON[taskState]}
        </TileGlyph>
      </GlyphTile>
    </span>
  );
}
