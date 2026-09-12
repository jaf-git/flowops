import { describe, expect, it } from 'vitest';

import { isMarkable, whyNotMarkable, type Markable } from './marking';

function markable(over: Partial<Markable> = {}): Markable {
  return { kind: 'SPOKEN', deletedAt: null, workNodeId: null, ...over };
}

describe('whether a message may be marked', () => {
  it('offers the circle on an ordinary spoken message', () => {
    expect(whyNotMarkable(markable())).toBeNull();
    expect(isMarkable(markable())).toBe(true);
  });

  it('refuses a work mark, because nobody said it', () => {
    expect(whyNotMarkable(markable({ kind: 'WORK_MARK' }))).toBe('NOT_SPOKEN');
  });

  it('refuses a withdrawn message, whose words are gone', () => {
    expect(whyNotMarkable(markable({ deletedAt: '2026-08-25T09:00:00Z' }))).toBe('WITHDRAWN');
  });

  it('refuses a message that has already become a unit of work', () => {
    expect(whyNotMarkable(markable({ workNodeId: 'node-1' }))).toBe('ALREADY_MARKED');
  });

  it('answers with a reason rather than a boolean, so a refusal can be explained in place', () => {
    const refusal = whyNotMarkable(markable({ deletedAt: '2026-08-25T09:00:00Z' }));

    expect(typeof refusal).toBe('string');
    expect(refusal).not.toBe(false);
    expect(isMarkable(markable({ deletedAt: '2026-08-25T09:00:00Z' }))).toBe(false);
  });

  it('does not refuse a message merely because it already became a task', () => {
    const converted = { ...markable(), convertedTaskId: 'task-1' };

    expect(whyNotMarkable(converted)).toBeNull();
  });

  it('treats an absent marked field as not yet marked', () => {
    expect(whyNotMarkable({ kind: 'SPOKEN' })).toBeNull();
  });
});
