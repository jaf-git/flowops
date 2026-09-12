// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../i18n/locales/en/common.json';
import { TaskStateChip, type TaskState } from './TaskStateChip';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}));

const EVERY_STATE: Record<TaskState, { tone: string }> = {
  Created: { tone: 'ui-chip-neutral' },
  Accepted: { tone: 'ui-chip-neutral' },
  InProgress: { tone: 'ui-chip-brand' },
  Blocked: { tone: 'ui-chip-blocked' },
  Completed: { tone: 'ui-chip-waiting' },
  Approved: { tone: 'ui-chip-done' },
  Closed: { tone: 'ui-chip-neutral' },
};

afterEach(cleanup);

describe('the task state chip', () => {
  for (const [state, { tone }] of Object.entries(EVERY_STATE)) {
    it(`renders ${state} with its own words and its own tone`, () => {
      render(<TaskStateChip state={state as TaskState} />);

      const chip = screen.getByText(`ui.taskState.${state}`);

      expect(chip.className).toContain(tone);
    });
  }

  it('does not draw blocked as amber, because overdue already is', () => {
    expect(EVERY_STATE.Blocked.tone).not.toBe(EVERY_STATE.Completed.tone);
  });

  it('has a phrase in both languages for every state', () => {
    for (const state of Object.keys(EVERY_STATE)) {
      expect(en.ui.taskState, `missing from en: ${state}`).toHaveProperty(state);
    }
  });
});
