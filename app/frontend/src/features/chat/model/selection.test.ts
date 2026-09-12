import { describe, expect, it } from 'vitest';

import {
  FEWEST_FOR_A_PROCESS,
  MOST_FOR_A_PROCESS,
  barIsOffered,
  isSelectable,
  toggled,
  whyNotBuildable,
  whyNotSelectable,
  type Selectable,
} from './selection';

function spoken(over: Partial<Selectable> = {}): Selectable {
  return { kind: 'SPOKEN', deletedAt: null, convertedTaskId: null, ...over };
}

function mark(): Selectable {
  return { kind: 'WORK_MARK' };
}

describe('what can be ticked', () => {
  it('offers an ordinary message', () => {
    expect(whyNotSelectable(spoken())).toBeNull();
    expect(isSelectable(spoken())).toBe(true);
  });

  it('refuses a message that already became a task, and says which rule it is', () => {
    expect(whyNotSelectable(spoken({ convertedTaskId: 't9' }))).toBe('ALREADY_WORK');
  });

  it('refuses a withdrawn message', () => {
    expect(whyNotSelectable(spoken({ deletedAt: '2026-08-21T10:00:00Z' }))).toBe('WITHDRAWN');
  });

  it('refuses a work mark, on its own grounds', () => {
    expect(whyNotSelectable(mark())).toBe('NOT_SPOKEN');
    expect(isSelectable(mark())).toBe(false);
  });

  it('tells the three reasons apart', () => {
    const reasons = [
      whyNotSelectable(spoken({ convertedTaskId: 't9' })),
      whyNotSelectable(spoken({ deletedAt: '2026-08-21T10:00:00Z' })),
      whyNotSelectable(mark()),
    ];

    expect(new Set(reasons).size).toBe(3);
  });
});

describe('what a selection may become', () => {
  const ids = (howMany: number): string[] => Array.from({ length: howMany }, (_, at) => `m${at}`);

  it('is not a process below two', () => {
    expect(whyNotBuildable([])).toBe('TOO_FEW');
    expect(whyNotBuildable(['m1'])).toBe('TOO_FEW');
    expect(barIsOffered(['m1'])).toBe(false);
  });

  it('is a process at two', () => {
    expect(whyNotBuildable(ids(FEWEST_FOR_A_PROCESS))).toBeNull();
    expect(barIsOffered(ids(FEWEST_FOR_A_PROCESS))).toBe(true);
  });

  it('is a process at the cap', () => {
    expect(whyNotBuildable(ids(MOST_FOR_A_PROCESS))).toBeNull();
  });

  it('is refused above the cap', () => {
    expect(whyNotBuildable(ids(MOST_FOR_A_PROCESS + 1))).toBe('TOO_MANY');
  });

  it('keeps offering itself above the cap so the refusal is visible', () => {
    expect(barIsOffered(ids(MOST_FOR_A_PROCESS + 1))).toBe(true);
    expect(whyNotBuildable(ids(MOST_FOR_A_PROCESS + 1))).toBe('TOO_MANY');
  });
});

describe('ticking and un-ticking', () => {
  const order = ['a', 'b', 'c', 'd'];

  it('adds and removes', () => {
    expect(toggled([], 'b', order)).toEqual(['b']);
    expect(toggled(['b'], 'b', order)).toEqual([]);
  });

  it('keeps the thread order however they were ticked', () => {
    let chosen: string[] = [];
    chosen = toggled(chosen, 'd', order);
    chosen = toggled(chosen, 'a', order);
    chosen = toggled(chosen, 'c', order);

    expect(chosen).toEqual(['a', 'c', 'd']);
  });

  it('does not double a message ticked twice in a row', () => {
    const once = toggled([], 'a', order);

    expect(toggled(toggled(once, 'a', order), 'a', order)).toEqual(['a']);
  });
});

describe('the shape these rules ask for', () => {
  it('needs only a kind, and tolerates a mark that carries nothing else', () => {
    expect(isSelectable({ kind: 'SPOKEN' })).toBe(true);
    expect(whyNotSelectable({ kind: 'WORK_MARK' })).toBe('NOT_SPOKEN');
  });
});
