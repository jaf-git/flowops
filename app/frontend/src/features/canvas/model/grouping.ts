export interface HeldStep {
  assigneeId: string | null;
}

export interface GroupableRun {
  id: string;
  categoryId?: string | null;
  steps: readonly HeldStep[];

  state?: 'RUNNING' | 'COMPLETE' | 'ABANDONED';
}

export type RunShape = 'SOLO' | 'SHARED' | 'UNSTAFFED';

export function soleHolderOf(run: GroupableRun): string | null {
  const holders = new Set(
    run.steps.map((step) => step.assigneeId).filter((who): who is string => who !== null),
  );
  return holders.size === 1 ? ([...holders][0] ?? null) : null;
}

export function shapeOf(run: GroupableRun): RunShape {
  const holders = new Set(
    run.steps.map((step) => step.assigneeId).filter((who): who is string => who !== null),
  );

  if (holders.size === 0) {
    return 'UNSTAFFED';
  }
  return holders.size === 1 ? 'SOLO' : 'SHARED';
}

export interface BoardLane<T> {
  key: string;

  personId?: string;
  runs: T[];
}

export interface Band<T> {
  key: string;
  kind: 'CATEGORY' | 'SHAPE';

  name?: string;
  lanes: BoardLane<T>[];
}

function oneLane<T>(key: string, runs: T[]): BoardLane<T>[] {
  return [{ key, runs }];
}

function lanesPerPerson<T extends GroupableRun>(
  runs: readonly T[],
  nameOf?: (personId: string) => string | undefined,
): BoardLane<T>[] {
  const byPerson = new Map<string, T[]>();

  for (const run of runs) {
    const holder = soleHolderOf(run);
    if (holder === null) {
      continue;
    }
    byPerson.set(holder, [...(byPerson.get(holder) ?? []), run]);
  }

  return [...byPerson.entries()]
    .map(([personId, held]) => ({ key: `SOLO:${personId}`, personId, runs: held }))
    .sort((a, b) => {
      const first = nameOf?.(a.personId);
      const second = nameOf?.(b.personId);
      if ((first === undefined) !== (second === undefined)) {
        return first === undefined ? 1 : -1;
      }
      return (first ?? a.personId).localeCompare(second ?? b.personId);
    });
}

export function bandsOf<T extends GroupableRun>(
  runs: readonly T[],
  categories: readonly { id: string; name: string }[],
  nameOf?: (personId: string) => string | undefined,
): Band<T>[] {
  const bands: Band<T>[] = [];

  const finished = runs.filter((run) => run.state !== undefined && run.state !== 'RUNNING');
  const live = runs.filter((run) => !finished.includes(run));

  for (const category of categories) {
    const under = live.filter((run) => run.categoryId === category.id);
    if (under.length > 0) {
      bands.push({
        key: category.id,
        kind: 'CATEGORY',
        name: category.name,
        lanes: oneLane(category.id, under),
      });
    }
  }

  const unfiled = live.filter(
    (run) =>
      run.categoryId == null || !categories.some((category) => category.id === run.categoryId),
  );

  for (const shape of ['UNSTAFFED', 'SHARED', 'SOLO'] as const) {
    const under = unfiled.filter((run) => shapeOf(run) === shape);
    if (under.length > 0) {
      bands.push({
        key: shape,
        kind: 'SHAPE',
        lanes: shape === 'SOLO' ? lanesPerPerson(under, nameOf) : oneLane(shape, under),
      });
    }
  }

  if (finished.length > 0) {
    bands.push({ key: 'DONE', kind: 'SHAPE', lanes: oneLane('DONE', finished) });
  }

  return bands;
}
