import type { TaskState } from '../../../shared/ui/TaskStateChip';
import type { CardTone, RunHealth } from './bands';
import type { StepCondition } from './node';

export type Tone = 'brand' | 'done' | 'waiting' | 'neutral' | 'blocked';

export const STATE_TONE: Record<TaskState, Tone> = {
  Created: 'neutral',
  Accepted: 'neutral',
  InProgress: 'brand',
  Blocked: 'blocked',
  Completed: 'waiting',
  Approved: 'done',
  Closed: 'neutral',
};

export const CONDITION_TONE: Record<StepCondition, Tone> = {
  Pending: 'neutral',
  Reachable: 'waiting',
  Assigned: 'brand',
  Closed: 'neutral',
};

export const HEALTH: Record<RunHealth, { band: string; edge: string; ink: string; soft: string }> =
  {
    onTrack: {
      band: 'color-mix(in srgb, var(--done) 5%, var(--surface))',
      edge: 'color-mix(in srgb, var(--done) 28%, var(--line))',
      ink: 'var(--done)',
      soft: 'var(--done-soft)',
    },
    atRisk: {
      band: 'color-mix(in srgb, var(--waiting) 6%, var(--surface))',
      edge: 'color-mix(in srgb, var(--waiting) 32%, var(--line))',
      ink: 'var(--waiting)',
      soft: 'color-mix(in srgb, var(--waiting) 12%, var(--surface))',
    },
    blocked: {
      band: 'color-mix(in srgb, var(--alert) 5%, var(--surface))',
      edge: 'var(--alert-line)',
      ink: 'var(--alert)',
      soft: 'color-mix(in srgb, var(--alert) 10%, var(--surface))',
    },
  };

export const TONE_COLOUR: Record<CardTone, string> = {
  notStarted: 'var(--faint)',
  inProgress: 'var(--brand)',
  attention: 'var(--waiting)',
  blocked: 'var(--alert)',
  done: 'var(--done)',
};
