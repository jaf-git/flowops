import { describe, expect, it } from 'vitest';

import { bandsOf, shapeOf, type GroupableRun } from './grouping';

function run(
  id: string,
  holders: (string | null)[],
  categoryId: string | null = null,
): GroupableRun {
  return { id, categoryId, steps: holders.map((assigneeId) => ({ assigneeId })) };
}

function runsIn(band: { lanes: { runs: { id: string }[] }[] } | undefined): { id: string }[] {
  return (band?.lanes ?? []).flatMap((lane) => lane.runs);
}

describe('how a run is shaped', () => {
  it('is solo when one person holds every step anybody holds', () => {
    expect(shapeOf(run('r1', ['ana', 'ana', 'ana']))).toBe('SOLO');
  });

  it('is still solo when some steps are unassigned', () => {
    expect(shapeOf(run('r1', ['ana', null, 'ana']))).toBe('SOLO');
  });

  it('is shared the moment work changes hands', () => {
    expect(shapeOf(run('r1', ['ana', 'mihai']))).toBe('SHARED');
  });

  it('is unstaffed when nobody holds anything, which is not the same as solo', () => {
    expect(shapeOf(run('r1', [null, null]))).toBe('UNSTAFFED');
    expect(shapeOf(run('r2', []))).toBe('UNSTAFFED');
  });
});

describe('dividing a board', () => {
  const categories = [
    { id: 'c1', name: 'Clients' },
    { id: 'c2', name: 'Internal' },
  ];

  it('puts named groups first, in the order given', () => {
    const bands = bandsOf([run('a', ['ana'], 'c2'), run('b', ['ana'], 'c1')], categories);

    expect(bands.map((band) => band.name)).toEqual(['Clients', 'Internal']);
  });

  it('shows a filed run under its name and nowhere else', () => {
    const bands = bandsOf([run('a', ['ana', 'mihai'], 'c1')], categories);

    expect(bands).toHaveLength(1);
    expect(bands[0]?.kind).toBe('CATEGORY');
    expect(bands.flatMap((band) => runsIn(band).map((each) => each.id))).toEqual(['a']);
  });

  it('bands whatever nobody filed by its shape', () => {
    const bands = bandsOf(
      [run('a', ['ana']), run('b', ['ana', 'mihai']), run('c', [null])],
      categories,
    );

    expect(bands.map((band) => band.key)).toEqual(['UNSTAFFED', 'SHARED', 'SOLO']);
  });

  it('drops empty bands, named ones included', () => {
    const bands = bandsOf([run('a', ['ana'])], categories);

    expect(bands.map((band) => band.key)).toEqual(['SOLO']);
  });

  it('bands a run whose category no longer exists', () => {
    const bands = bandsOf([run('a', ['ana'], 'deleted')], categories);

    expect(bands.map((band) => band.key)).toEqual(['SOLO']);
    expect(runsIn(bands[0]).map((each) => each.id)).toEqual(['a']);
  });

  it('shows every run exactly once, always', () => {
    const runs = [
      run('a', ['ana'], 'c1'),
      run('b', ['ana', 'mihai']),
      run('c', [null]),
      run('d', ['ana']),
      run('e', ['x'], 'gone'),
    ];

    const shown = bandsOf(runs, categories).flatMap((band) => runsIn(band).map((each) => each.id));

    expect(shown).toHaveLength(runs.length);
    expect(new Set(shown).size).toBe(runs.length);
  });
});

describe("one person's own, divided", () => {
  const names: Record<string, string> = {
    ana: 'Ana Vlad',
    mihai: 'Mihai Georgescu',
    zoe: 'Zoe Toma',
  };

  const nameOf = (id: string): string | undefined => names[id];

  it('gives each person their own lane', () => {
    const bands = bandsOf([run('a', ['ana']), run('b', ['mihai']), run('c', ['ana'])], [], nameOf);

    const solo = bands.find((band) => band.key === 'SOLO');
    expect(solo?.lanes.map((lane) => lane.personId)).toEqual(['ana', 'mihai']);
    expect(solo?.lanes[0]?.runs.map((each) => each.id)).toEqual(['a', 'c']);
  });

  it('orders the lanes by name, however much anybody holds', () => {
    const bands = bandsOf(
      [run('a', ['zoe']), run('b', ['ana']), run('c', ['ana']), run('d', ['ana'])],
      [],
      nameOf,
    );

    const solo = bands.find((band) => band.key === 'SOLO');

    expect(solo?.lanes.map((lane) => lane.personId)).toEqual(['ana', 'zoe']);
  });

  it('still orders them stably when no name is known at all', () => {
    const bands = bandsOf([run('a', ['zoe']), run('b', ['ana'])], []);

    expect(bands.find((band) => band.key === 'SOLO')?.lanes.map((lane) => lane.personId)).toEqual([
      'ana',
      'zoe',
    ]);
  });

  it('puts a lane with no known name last rather than wherever its identifier falls', () => {
    const bands = bandsOf(
      [run('a', ['aaa-gone']), run('b', ['mihai']), run('c', ['ana'])],
      [],
      nameOf,
    );

    expect(bands.find((band) => band.key === 'SOLO')?.lanes.map((lane) => lane.personId)).toEqual([
      'ana',
      'mihai',
      'aaa-gone',
    ]);
  });

  it('leaves every other group as one nameless lane', () => {
    const bands = bandsOf([run('a', ['ana', 'mihai']), run('b', [null])], [], nameOf);

    for (const band of bands) {
      expect(band.lanes).toHaveLength(1);
      expect(band.lanes[0]?.personId).toBeUndefined();
    }
  });

  it('shows every run exactly once even when the lanes divide them', () => {
    const runs = [
      run('a', ['ana']),
      run('b', ['mihai']),
      run('c', ['ana', 'mihai']),
      run('d', [null]),
    ];

    const shown = bandsOf(runs, [], nameOf).flatMap((band) => runsIn(band).map((each) => each.id));

    expect(new Set(shown).size).toBe(runs.length);
    expect(shown).toHaveLength(runs.length);
  });
});
