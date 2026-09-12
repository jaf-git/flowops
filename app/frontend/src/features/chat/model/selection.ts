export interface Selectable {
  kind: 'SPOKEN' | 'WORK_MARK';
  deletedAt?: string | null;
  convertedTaskId?: string | null;
}

export const FEWEST_FOR_A_PROCESS = 2;

export const MOST_FOR_A_PROCESS = 50;

export type NotSelectable = 'ALREADY_WORK' | 'WITHDRAWN' | 'NOT_SPOKEN';

export function whyNotSelectable(entry: Selectable): NotSelectable | null {
  if (entry.kind !== 'SPOKEN') {
    return 'NOT_SPOKEN';
  }
  if (entry.deletedAt != null) {
    return 'WITHDRAWN';
  }

  if (entry.convertedTaskId != null) {
    return 'ALREADY_WORK';
  }
  return null;
}

export function isSelectable(entry: Selectable): boolean {
  return whyNotSelectable(entry) === null;
}

export type SelectionRefusal = 'TOO_FEW' | 'TOO_MANY';

export function whyNotBuildable(chosen: readonly string[]): SelectionRefusal | null {
  if (chosen.length < FEWEST_FOR_A_PROCESS) {
    return 'TOO_FEW';
  }
  if (chosen.length > MOST_FOR_A_PROCESS) {
    return 'TOO_MANY';
  }
  return null;
}

export function barIsOffered(chosen: readonly string[]): boolean {
  return chosen.length >= FEWEST_FOR_A_PROCESS;
}

export function toggled(
  chosen: readonly string[],
  messageId: string,
  inThreadOrder: readonly string[],
): string[] {
  const next = new Set(chosen);
  if (next.has(messageId)) {
    next.delete(messageId);
  } else {
    next.add(messageId);
  }
  return inThreadOrder.filter((id) => next.has(id));
}
