import { describe, expect, it } from 'vitest';

import { todayGroupOf, todayGroups, type TodayTaskInput } from './today';

const NOW = new Date('2026-08-19T12:00:00Z');

function task(over: Partial<TodayTaskInput> = {}): TodayTaskInput {
  return { state: 'IN_PROGRESS', deadline: null, atRisk: false, phaseSince: null, ...over };
}

describe('the Today grouping', () => {
  it('puts blocked work under needs attention', () => {
    expect(todayGroupOf(task({ state: 'BLOCKED' }), NOW)).toBe('attention');
  });

  it('puts at-risk and overdue work there too, because all three are the same question', () => {
    expect(todayGroupOf(task({ atRisk: true }), NOW)).toBe('attention');
    expect(todayGroupOf(task({ deadline: '2026-08-18T09:00:00Z' }), NOW)).toBe('attention');
  });

  it('puts work due today under due today', () => {
    expect(todayGroupOf(task({ deadline: '2026-08-19T17:00:00Z' }), NOW)).toBe('dueToday');
  });

  it('prefers needs attention over due today when a task is both', () => {
    expect(todayGroupOf(task({ state: 'BLOCKED', deadline: '2026-08-19T17:00:00Z' }), NOW)).toBe(
      'attention',
    );
  });

  it('calls a phase stalled after two days, and not before', () => {
    expect(todayGroupOf(task({ phaseSince: '2026-08-17T09:00:00Z' }), NOW)).toBe('stalled');
    expect(todayGroupOf(task({ phaseSince: '2026-08-18T09:00:00Z' }), NOW)).toBeNull();
  });

  it('leaves closed and approved work out of every group', () => {
    expect(
      todayGroupOf(task({ state: 'CLOSED', phaseSince: '2026-07-01T09:00:00Z' }), NOW),
    ).toBeNull();
    expect(todayGroupOf(task({ state: 'APPROVED', atRisk: true }), NOW)).toBeNull();
  });

  it('returns all three groups even when they are empty, in the screen order', () => {
    const groups = todayGroups([], NOW);

    expect(groups.map((each) => each.group)).toEqual(['attention', 'dueToday', 'stalled']);
    expect(groups.every((each) => each.tasks.length === 0)).toBe(true);
  });

  it('puts each task in exactly one group', () => {
    const tasks = [
      task({ state: 'BLOCKED', deadline: '2026-08-19T17:00:00Z' }),
      task({ deadline: '2026-08-19T17:00:00Z' }),
      task({ phaseSince: '2026-08-01T09:00:00Z' }),
      task(),
    ];

    const grouped = todayGroups(tasks, NOW);

    expect(grouped.flatMap((each) => each.tasks)).toHaveLength(3);
    expect(grouped.map((each) => each.tasks.length)).toEqual([1, 1, 1]);
  });
});
