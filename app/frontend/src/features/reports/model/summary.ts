export interface ReportStepInput {
  id: string;
  title: string;
  position: number;
  condition: 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';
  taskId: string | null;
  taskState: string | null;
  blockedReason: string | null;
  expectedDurationHours: number | null;
}

export interface ReportRunInput {
  id: string;
  name: string;

  state: 'RUNNING' | 'COMPLETE' | 'ABANDONED';
  progress: { closed: number; total: number };

  awaitingAssignment: readonly string[];
  bottleneck: { stepId: string; waitedMinutes: number } | null;
  steps: readonly ReportStepInput[];
}

export type ReportState = 'notStarted' | 'inProgress' | 'blocked' | 'done';

export const REPORT_STATES: readonly ReportState[] = [
  'done',
  'inProgress',
  'blocked',
  'notStarted',
];

export interface DistributionRow {
  state: ReportState;
  count: number;

  share: number;
}

export interface WaitingRow {
  runId: string;
  runName: string;
  stepTitle: string;
  minutes: number;

  share: number;
}

export interface ReportSummary {
  runs: number;
  activeRuns: number;
  closed: number;
  total: number;
  completionPercent: number;
  blocked: number;
  awaitingAssignment: number;

  needsAttention: number;

  openHours: number;

  unestimatedOpen: number;
  distribution: readonly DistributionRow[];
  waiting: readonly WaitingRow[];
}

export function summarise(
  runs: readonly ReportRunInput[],
  stateOf: (step: ReportStepInput) => ReportState,
): ReportSummary {
  const counts = new Map<ReportState, number>(REPORT_STATES.map((state) => [state, 0]));
  const attention = new Set<string>();
  let steps = 0;
  let blocked = 0;
  let awaiting = 0;
  let closed = 0;
  let total = 0;
  let openHours = 0;
  let unestimatedOpen = 0;

  for (const run of runs) {
    closed += run.progress.closed;
    total += run.progress.total;
    awaiting += run.awaitingAssignment.length;
    for (const stepId of run.awaitingAssignment) {
      attention.add(stepId);
    }

    for (const step of run.steps) {
      const state = stateOf(step);
      steps += 1;
      counts.set(state, (counts.get(state) ?? 0) + 1);

      if (state === 'blocked') {
        blocked += 1;
        attention.add(step.id);
      }

      if (state !== 'done') {
        if (step.expectedDurationHours === null) {
          unestimatedOpen += 1;
        } else {
          openHours += step.expectedDurationHours;
        }
      }
    }
  }

  return {
    runs: runs.length,
    activeRuns: runs.filter((run) => run.state === 'RUNNING').length,
    closed,
    total,
    completionPercent: total === 0 ? 0 : Math.round((closed / total) * 100),
    blocked,
    awaitingAssignment: awaiting,
    needsAttention: attention.size,

    openHours: Math.round(openHours * 100) / 100,
    unestimatedOpen,
    distribution: REPORT_STATES.map((state) => {
      const count = counts.get(state) ?? 0;
      return { state, count, share: steps === 0 ? 0 : count / steps };
    }),
    waiting: waitingRows(runs, stateOf),
  };
}

function waitingRows(
  runs: readonly ReportRunInput[],
  stateOf: (step: ReportStepInput) => ReportState,
): WaitingRow[] {
  const rows: Omit<WaitingRow, 'share'>[] = [];

  for (const run of runs) {
    if (run.state !== 'RUNNING' || run.bottleneck === null) {
      continue;
    }
    const held = run.steps.find((step) => step.id === run.bottleneck?.stepId);
    if (held === undefined || stateOf(held) === 'done') {
      continue;
    }
    rows.push({
      runId: run.id,
      runName: run.name,
      stepTitle: held.title,
      minutes: run.bottleneck.waitedMinutes,
    });
  }

  rows.sort((a, b) => b.minutes - a.minutes);
  const longest = rows.reduce((worst, row) => Math.max(worst, row.minutes), 0);

  return rows.map((row) => ({ ...row, share: longest === 0 ? 0 : row.minutes / longest }));
}
