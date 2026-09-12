import type { GraphEdge, Translate } from './layout';
import type { CanvasNode, NodeMetaRow, StepCondition } from './node';
import type { TaskLifecycleState } from '../../../shared/ui/TaskActionRail';
import type { TaskState } from '../../../shared/ui/TaskStateChip';
import type { Phase } from '../../../shared/ui/PhaseBreakdown';

export interface InstanceStepPayload {
  id: string;

  title: string;
  position: number;
  condition: 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';
  taskId: string | null;
  taskState: string | null;
  blockedReason: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  deadline: string | null;
  atRisk: boolean;
  phases: Array<{ kind: string; seconds: number }>;
}

export interface InstanceEdgePayload {
  from: string;
  to: string;
  satisfied: boolean;
}

export interface InstancePayload {
  id: string;
  name: string;
  state: string;
  progress: { closed: number; total: number };
  steps: InstanceStepPayload[];
  edges: InstanceEdgePayload[];
  awaitingAssignment: string[];
}

const CONDITION: Record<InstanceStepPayload['condition'], StepCondition> = {
  PENDING: 'Pending',
  REACHABLE: 'Reachable',
  ASSIGNED: 'Assigned',
  CLOSED: 'Closed',
};

const TASK_STATE: Record<string, TaskState> = {
  CREATED: 'Created',
  ACCEPTED: 'Accepted',
  IN_PROGRESS: 'InProgress',
  BLOCKED: 'Blocked',
  COMPLETED: 'Completed',
  APPROVED: 'Approved',
  CLOSED: 'Closed',
};

const LIFECYCLE: readonly TaskLifecycleState[] = [
  'CREATED',
  'ACCEPTED',
  'IN_PROGRESS',
  'BLOCKED',
  'COMPLETED',
  'APPROVED',
  'CLOSED',
];

const PHASE: Record<string, Phase> = {
  ACTIVE: 'active',
  BLOCKED: 'blocked',
  WAIT: 'wait',
  REVIEW: 'review',
  APPROVAL: 'approval',
};

export function lifecycleStateOfStep(step: InstanceStepPayload): TaskLifecycleState | undefined {
  if (step.taskState === null) {
    return undefined;
  }

  return LIFECYCLE.includes(step.taskState as TaskLifecycleState)
    ? (step.taskState as TaskLifecycleState)
    : undefined;
}

export function taskStateOfStep(step: InstanceStepPayload): TaskState | undefined {
  return step.taskState === null ? undefined : TASK_STATE[step.taskState];
}

export function phasesOfStep(step: InstanceStepPayload): Array<{ phase: Phase; seconds: number }> {
  return step.phases
    .map((span) => ({ phase: PHASE[span.kind], seconds: span.seconds }))
    .filter((span): span is { phase: Phase; seconds: number } => span.phase !== undefined);
}

function waitsOn(
  step: InstanceStepPayload,
  edges: readonly InstanceEdgePayload[],
  numberOfStep: Map<string, number>,
  t: Translate,
): NodeMetaRow | undefined {
  if (step.condition !== 'PENDING') {
    return undefined;
  }

  const holding = edges
    .filter((edge) => edge.to === step.id && !edge.satisfied)
    .map((edge) => numberOfStep.get(edge.from))
    .filter((number): number is number => number !== undefined)
    .sort((a, b) => a - b);

  if (holding.length === 0) {
    return undefined;
  }

  return {
    label: t('canvas.node.waitsOn'),
    value: t(holding.length === 1 ? 'canvas.node.waitsOnStep' : 'canvas.node.waitsOnSteps', {
      steps: holding.join(', '),
    }),
  };
}

export function nodesFromInstance(
  instance: InstancePayload,
  t: Translate,
): {
  steps: CanvasNode[];
  edges: GraphEdge[];
} {
  const awaiting = new Set(instance.awaitingAssignment);
  const numberOfStep = new Map(instance.steps.map((step) => [step.id, step.position + 1]));

  const steps = instance.steps.map((step): CanvasNode => {
    const phases = phasesOfStep(step);
    const waiting = waitsOn(step, instance.edges, numberOfStep, t);

    return {
      meta: waiting === undefined ? undefined : [waiting],
      id: step.id,
      title: step.title,
      stepNumber: step.position + 1,
      condition: CONDITION[step.condition],
      taskState: taskStateOfStep(step),
      assignee:
        step.assigneeId === null
          ? undefined
          : { name: step.assigneeName === '' ? null : step.assigneeName },
      dueAt: step.deadline,
      atRisk: step.atRisk,
      blockedReason: step.blockedReason ?? undefined,
      phases: phases.length === 0 ? undefined : phases,
      offersAssignment: awaiting.has(step.id),
    };
  });

  return {
    steps,
    edges: instance.edges.map((edge) => ({
      from: edge.from,
      to: edge.to,
      satisfied: edge.satisfied,
    })),
  };
}
