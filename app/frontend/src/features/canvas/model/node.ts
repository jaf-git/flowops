import type { PhaseSpan } from '../../../shared/ui/PhaseBreakdown';
import type { TaskState } from '../../../shared/ui/TaskStateChip';

export type StepCondition = 'Pending' | 'Reachable' | 'Assigned' | 'Closed';

export interface NodeAssignee {
  name: string | null;
  erased?: boolean;
}

export interface NodeMetaRow {
  label: string;
  value: string;
  tone?: 'alert' | 'warn';
}

export interface CanvasNode {
  id: string;
  title: string;
  condition: StepCondition;
  stepNumber?: number;
  taskState?: TaskState;
  assignee?: NodeAssignee;
  dueAt?: string | null;
  atRisk?: boolean;
  blockedReason?: string;
  reworkNote?: string;
  phases?: readonly PhaseSpan[];
  meta?: readonly NodeMetaRow[];
  offersAssignment?: boolean;
}

export type BorderTreatment = 'overdue' | 'blocked' | 'ready' | 'selected' | 'pending' | 'default';

export function borderTreatmentOf(
  node: CanvasNode,
  options: { overdue?: boolean; selected?: boolean } = {},
): BorderTreatment {
  if (options.overdue === true) {
    return 'overdue';
  }

  if (node.blockedReason !== undefined) {
    return 'blocked';
  }

  if (node.condition === 'Reachable') {
    return 'ready';
  }

  if (options.selected === true) {
    return 'selected';
  }

  return node.condition === 'Pending' ? 'pending' : 'default';
}

export function isOverdue(node: CanvasNode, now: Date): boolean {
  if (node.condition === 'Pending' || node.dueAt === undefined || node.dueAt === null) {
    return false;
  }

  const due = new Date(node.dueAt);

  return !Number.isNaN(due.getTime()) && due.getTime() < now.getTime();
}
