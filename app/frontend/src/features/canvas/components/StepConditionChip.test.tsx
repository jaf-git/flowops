// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import type { StepCondition } from '../model/node';
import { StepConditionChip } from './StepConditionChip';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      return typeof phrase === 'string' ? phrase : key;
    },
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

const EVERY_CONDITION: readonly StepCondition[] = ['Pending', 'Reachable', 'Assigned', 'Closed'];

describe('the condition a step is in', () => {
  it.each(EVERY_CONDITION)('says %s in words, never in colour alone', (condition) => {
    render(<StepConditionChip condition={condition} />);

    expect(screen.getByText(en.canvas.condition[condition])).toBeTruthy();
  });

  it('gives each condition its own word, so two never read alike', () => {
    const words = EVERY_CONDITION.map((condition) => en.canvas.condition[condition]);

    expect(new Set(words).size).toBe(EVERY_CONDITION.length);
  });
});
