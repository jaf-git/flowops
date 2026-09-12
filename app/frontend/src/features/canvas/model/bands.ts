export interface BandStepInput {
  id: string;
  title: string;
  position: number;
  condition: 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';
  taskId: string | null;
  taskState: string | null;
  blockedReason: string | null;

  dependsOn: string[];

  description: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  deadline: string | null;
  atRisk: boolean;
}

export interface BandInstanceInput {
  id: string;
  name: string;

  state: 'RUNNING' | 'COMPLETE' | 'ABANDONED';
  processOwnerId: string;
  progress: { closed: number; total: number };
  bottleneck: { stepId: string; waitedMinutes: number } | null;
  steps: BandStepInput[];
}

export type CardState = 'notStarted' | 'inProgress' | 'blocked' | 'done';

export interface BandCard {
  id: string;
  title: string;

  taskId: string | null;
  state: CardState;

  blockedReason: string | null;

  taskState: string | null;

  bottleneckMinutes: number | null;

  description: string | null;

  assigneeId: string | null;

  assigneeName: string | null;
  deadline: string | null;

  atRisk: boolean;

  dependsOn: string[];
}

export interface Band {
  id: string;
  name: string;
  running: boolean;

  paused: boolean;
  ownerId: string;
  progress: { closed: number; total: number };
  cards: BandCard[];
}

export type RunHealth = 'blocked' | 'atRisk' | 'onTrack';

export function healthOf(cards: readonly BandCard[]): RunHealth {
  if (cards.some((card) => card.state === 'blocked')) {
    return 'blocked';
  }
  if (cards.some((card) => card.atRisk && card.state !== 'done')) {
    return 'atRisk';
  }
  return 'onTrack';
}

export function stepPosition(band: {
  progress: { closed: number; total: number };
}): { at: number; of: number } | null {
  const { closed, total } = band.progress;
  if (total === 0 || closed >= total) {
    return null;
  }
  return { at: closed + 1, of: total };
}

export function readyForSomebody(cards: readonly BandCard[]): BandCard | null {
  return cards.find((card) => card.state !== 'done' && card.assigneeId === null) ?? null;
}

export function unsequencedIn(cards: readonly BandCard[]): BandCard[] {
  if (cards.length < 2) {
    return [];
  }
  const dependedOn = new Set(cards.flatMap((card) => card.dependsOn));
  return cards.filter((card) => card.dependsOn.length === 0 && !dependedOn.has(card.id));
}

export type CardTone = 'done' | 'blocked' | 'attention' | 'inProgress' | 'notStarted';

export function cardTone(card: Pick<BandCard, 'state' | 'atRisk'>): CardTone {
  if (card.state === 'done' || card.state === 'blocked') {
    return card.state;
  }
  return card.atRisk ? 'attention' : card.state;
}

const NOT_BEGUN: readonly string[] = ['CREATED', 'ACCEPTED'];

export function cardState(step: {
  condition: BandStepInput['condition'];
  blockedReason: string | null;
  taskState: string | null;
}): CardState {
  if (step.condition === 'CLOSED') {
    return 'done';
  }
  if (step.blockedReason !== null || step.taskState === 'BLOCKED') {
    return 'blocked';
  }
  if (step.condition === 'ASSIGNED') {
    return NOT_BEGUN.includes(step.taskState ?? '') ? 'notStarted' : 'inProgress';
  }
  return 'notStarted';
}

export function bandsFrom(instances: readonly BandInstanceInput[]): Band[] {
  return instances.map((instance) => ({
    id: instance.id,
    name: instance.name,
    running: instance.state === 'RUNNING',
    paused: instance.state === 'ABANDONED',
    ownerId: instance.processOwnerId,
    progress: instance.progress,
    cards: [...instance.steps]
      .sort((a, b) => a.position - b.position)
      .map((step) => ({
        id: step.id,
        title: step.title,
        taskId: step.taskId,
        state: cardState(step),
        blockedReason: step.blockedReason,
        taskState: step.taskState,
        description: step.description,
        assigneeId: step.assigneeId,
        assigneeName: step.assigneeName,
        deadline: step.deadline,
        atRisk: step.atRisk,
        dependsOn: step.dependsOn,
        bottleneckMinutes:
          instance.bottleneck !== null && instance.bottleneck.stepId === step.id
            ? instance.bottleneck.waitedMinutes
            : null,
      })),
  }));
}
