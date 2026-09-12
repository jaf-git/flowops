export interface TodayTaskInput {
  state: string;
  deadline: string | null;
  atRisk: boolean;

  phaseSince: string | null;
}

export type TodayGroup = 'attention' | 'dueToday' | 'stalled';

const STALLED_AFTER_HOURS = 48;

export function todayGroupOf(task: TodayTaskInput, now: Date): TodayGroup | null {
  if (task.state === 'CLOSED' || task.state === 'APPROVED') {
    return null;
  }

  const deadline = task.deadline === null ? null : new Date(task.deadline);

  if (task.state === 'BLOCKED' || task.atRisk || (deadline !== null && deadline < now)) {
    return 'attention';
  }

  if (deadline !== null && isSameDay(deadline, now)) {
    return 'dueToday';
  }

  if (
    task.phaseSince !== null &&
    hoursBetween(new Date(task.phaseSince), now) >= STALLED_AFTER_HOURS
  ) {
    return 'stalled';
  }

  return null;
}

export function todayGroups<T extends TodayTaskInput>(
  tasks: readonly T[],
  now: Date,
): ReadonlyArray<{ group: TodayGroup; tasks: T[] }> {
  const order: TodayGroup[] = ['attention', 'dueToday', 'stalled'];
  return order.map((group) => ({
    group,
    tasks: tasks.filter((task) => todayGroupOf(task, now) === group),
  }));
}

function isSameDay(left: Date, right: Date): boolean {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  );
}

function hoursBetween(from: Date, to: Date): number {
  return (to.getTime() - from.getTime()) / 3_600_000;
}
