import { describe, expect, it } from 'vitest';

import { findPerson, type SearchableRun } from './findPerson';

const runs: SearchableRun[] = [
  {
    id: 'brand',
    ownerName: 'Ioana Radu',
    cards: [
      { id: 'brief', assigneeName: 'Ionuț Petrescu' },
      { id: 'moodboards', assigneeName: 'Ioana Radu' },
      { id: 'handover', assigneeName: null },
    ],
  },
  {
    id: 'launch',
    ownerName: 'Ștefan Popa',
    cards: [
      { id: 'copy', assigneeName: 'Ionuț Petrescu' },
      { id: 'photos', assigneeName: 'Raluca Sandu' },
    ],
  },
  {
    id: 'audit',
    ownerName: null,
    cards: [{ id: 'pull', assigneeName: null }],
  },
];

describe('finding one person on a board of everybody', () => {
  it('does nothing at all until somebody is looking for something', () => {
    expect(findPerson('', runs).active).toBe(false);
    expect(findPerson('   ', runs).active).toBe(false);
  });

  it('finds the runs somebody owns', () => {
    expect([...findPerson('Ioana', runs).runs]).toContain('brand');
  });

  it('finds a step somebody holds inside a run they do not own', () => {
    const found = findPerson('Raluca', runs);

    expect([...found.runs]).toEqual(['launch']);
    expect([...found.cards]).toEqual(['photos']);
  });

  it('marks every run a person appears in, not only the first', () => {
    expect([...findPerson('Ionuț', runs).runs].sort()).toEqual(['brand', 'launch']);
  });

  it('finds a Romanian name typed without its diacritics', () => {
    expect([...findPerson('Ionut', runs).runs].sort()).toEqual(['brand', 'launch']);
    expect([...findPerson('stefan', runs).runs]).toEqual(['launch']);
  });

  it('answers plainly when nobody by that name holds anything', () => {
    const found = findPerson('Cosmin', runs);

    expect(found.active).toBe(true);
    expect(found.runs.size).toBe(0);
  });

  it('never drops a run from the answer, only marks the ones that matched', () => {
    const found = findPerson('Raluca', runs);

    expect(found.runs.has('brand')).toBe(false);
    expect(found.runs.has('audit')).toBe(false);
  });

  it('does not fall over on a run nobody owns or holds', () => {
    expect(() => findPerson('anybody', runs)).not.toThrow();
  });
});
