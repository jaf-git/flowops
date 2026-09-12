export type NotificationGroup = 'ASSIGNMENT' | 'TIME' | 'PROCESS' | 'WEEKLY' | 'ESCALATION';

export type NotificationSubjectKind = 'TASK' | 'STEP' | 'RUN' | 'WORKSPACE' | 'BRACKET' | 'JOB';

export interface NotificationRow {
  id: string;
  kind: string;
  group: NotificationGroup;
  subjectKind: NotificationSubjectKind;
  subjectId: string | null;

  createdAt: string;

  readAt: string | null;
  items: NotificationRow[];
}

export interface NotificationPreferences {
  assignment: boolean;
  time: boolean;
  process: boolean;
  weekly: boolean;
}

export const PREFERENCE_SWITCHES: ReadonlyArray<keyof NotificationPreferences> = [
  'assignment',
  'time',
  'process',
  'weekly',
];

export const NOTIFICATION_KINDS: readonly string[] = [
  'WORK_ASSIGNED',
  'WORK_RETURNED',
  'WORK_APPROVED',
  'WORK_REJECTED',
  'DEADLINE_PROPOSED',
  'DEADLINE_DECIDED',
  'LONG_BLOCK_1',
  'LONG_BLOCK_2',
  'STALE_REVIEW',
  'OVERDUE_RUNG_1',
  'OVERDUE_RUNG_2',
  'OVERDUE_RUNG_3',
  'STEP_REACHABLE',
  'STEP_STALLED',
  'RUN_COMPLETED',
  'TASK_ATTACHED_TO_RUN',
  'WEEKLY_SUMMARY',

  'AWAITED_WORK_ARRIVED',
  'AWAITED_WORK_DROPPED',
  'BRACKET_STILL_GOING',
  'JOB_FORCE_CLOSED',
  'SOMEBODY_IS_WAITING_ON_YOU',
  'WAIT_DATE_HAS_PASSED',
  'EXTERNAL_WAIT_IS_LONG',

  'A_TEMPLATE_EXISTS_FOR_THIS',
  'AN_ENGAGEMENT_NEEDS_A_LOOK',
  'SOMETHING_IS_WORTH_WRITING_DOWN',
];

export function sentenceKeyFor(kind: string): string {
  return `notification.kind.${NOTIFICATION_KINDS.includes(kind) ? kind : 'UNKNOWN'}`;
}

export function isDigest(row: NotificationRow): boolean {
  return row.items.length > 0;
}

export function inboxOrder(rows: readonly NotificationRow[]): NotificationRow[] {
  return [...rows].sort((left, right) => {
    if ((left.readAt === null) !== (right.readAt === null)) {
      return left.readAt === null ? -1 : 1;
    }
    return right.createdAt.localeCompare(left.createdAt);
  });
}

export interface NotificationSubject {
  kind: NotificationSubjectKind;
  id: string;
}

export function subjectOf(row: NotificationRow): NotificationSubject | undefined {
  if (isDigest(row) || row.subjectId === null) {
    return undefined;
  }
  return { kind: row.subjectKind, id: row.subjectId };
}
