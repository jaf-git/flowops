export interface Markable {
  kind: 'SPOKEN' | 'WORK_MARK';
  deletedAt?: string | null;

  workNodeId?: string | null;
}

export type NotMarkable = 'ALREADY_MARKED' | 'WITHDRAWN' | 'NOT_SPOKEN';

export function whyNotMarkable(entry: Markable): NotMarkable | null {
  if (entry.kind !== 'SPOKEN') {
    return 'NOT_SPOKEN';
  }

  if (entry.deletedAt != null) {
    return 'WITHDRAWN';
  }

  if (entry.workNodeId != null) {
    return 'ALREADY_MARKED';
  }
  return null;
}

export function isMarkable(entry: Markable): boolean {
  return whyNotMarkable(entry) === null;
}
