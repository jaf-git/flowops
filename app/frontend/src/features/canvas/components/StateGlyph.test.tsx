// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { TaskStateChip, type TaskState } from '../../../shared/ui/TaskStateChip';
import type { StepCondition } from '../model/node';
import { CONDITION_TONE, STATE_TONE } from '../model/tone';
import { StateGlyph } from './StateGlyph';
import { StepConditionChip } from './StepConditionChip';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}));

afterEach(cleanup);

const EVERY_STATE: readonly TaskState[] = [
  'Created',
  'Accepted',
  'InProgress',
  'Blocked',
  'Completed',
  'Approved',
  'Closed',
];

const EVERY_CONDITION: readonly StepCondition[] = ['Pending', 'Reachable', 'Assigned', 'Closed'];

function toneOf(element: Element): string {
  return (
    [...element.classList].find((name) => name.startsWith('ui-chip-'))?.replace('ui-chip-', '') ??
    ''
  );
}

describe('the glyph on a node', () => {
  it.each(EVERY_STATE)('draws an icon for %s, so colour never carries the state alone', (state) => {
    render(<StateGlyph condition="Assigned" taskState={state} />);

    expect(screen.getByTestId('canvas-node-glyph').querySelector('svg')).toBeTruthy();
  });

  it.each(EVERY_CONDITION)('draws an icon for a %s step too', (condition) => {
    render(<StateGlyph condition={condition} />);

    expect(screen.getByTestId('canvas-node-glyph').querySelector('svg')).toBeTruthy();
  });

  it.each(EVERY_STATE)('agrees with the state chip about what colour %s is', (state) => {
    const { container } = render(<TaskStateChip state={state} />);
    const chip = container.querySelector('.ui-chip');

    expect(chip).toBeTruthy();
    expect(toneOf(chip as Element)).toBe(STATE_TONE[state]);
  });

  it.each(EVERY_CONDITION)(
    'agrees with the condition chip about what %s looks like',
    (condition) => {
      const { container } = render(<StepConditionChip condition={condition} />);
      const chip = container.querySelector('.ui-chip');

      expect(chip).toBeTruthy();
      expect(toneOf(chip as Element)).toBe(CONDITION_TONE[condition]);
    },
  );
});
