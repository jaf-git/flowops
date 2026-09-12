import type { GraphEdge, Translate } from './layout';
import type { CanvasNode } from './node';

const HOUR = 60 * 60;

export const INSTANCE_NOW = new Date('2026-08-13T09:00:00Z');

export interface InstanceFixture {
  steps: CanvasNode[];
  edges: GraphEdge[];
}

export function instanceFixture(t: Translate): InstanceFixture {
  const step = (id: string, node: Omit<CanvasNode, 'id' | 'title'>): CanvasNode => ({
    ...node,
    id,
    title: t(`canvas.instance.titles.${id}`),
  });

  return {
    steps: [
      step('1', {
        stepNumber: 1,
        condition: 'Closed',
        taskState: 'Closed',
        assignee: { name: 'Maria Pop' },
        phases: [
          { phase: 'active', seconds: 6 * HOUR },
          { phase: 'wait', seconds: 22 * HOUR },
        ],
      }),
      step('2', {
        stepNumber: 2,
        condition: 'Closed',
        taskState: 'Closed',
        assignee: { name: 'Radu Cîmpean' },
        phases: [
          { phase: 'active', seconds: 4 * HOUR },
          { phase: 'wait', seconds: 9 * HOUR },
        ],
      }),
      step('3', {
        stepNumber: 3,
        condition: 'Assigned',
        taskState: 'InProgress',
        assignee: { name: 'Ana Neagu' },
        dueAt: '2026-08-15T15:00:00Z',
        atRisk: true,
        phases: [
          { phase: 'active', seconds: 3 * HOUR },
          { phase: 'wait', seconds: 5 * HOUR },
        ],
      }),
      step('4', {
        stepNumber: 4,
        condition: 'Assigned',
        taskState: 'Blocked',
        assignee: { name: 'Dan Stan' },
        dueAt: '2026-08-11T15:00:00Z',
        blockedReason: t('canvas.gallery.reasons.licence'),
        phases: [
          { phase: 'active', seconds: 2 * HOUR },
          { phase: 'blocked', seconds: 72 * HOUR },
        ],
      }),
      step('5', {
        stepNumber: 5,
        condition: 'Reachable',
        meta: [
          { label: t('canvas.node.unlocked'), value: t('canvas.gallery.unlockedBy') },
          { label: t('canvas.node.waiting'), value: t('canvas.gallery.waitedDays'), tone: 'warn' },
        ],
        offersAssignment: true,
      }),
      step('6', {
        stepNumber: 6,
        condition: 'Pending',
        meta: [{ label: t('canvas.node.waitsOn'), value: t('canvas.instance.waitsOn6') }],
      }),
      step('7', {
        stepNumber: 7,
        condition: 'Pending',
        meta: [{ label: t('canvas.node.waitsOn'), value: t('canvas.instance.waitsOn7') }],
      }),
    ],
    edges: [
      { from: '1', to: '3', satisfied: true },
      { from: '1', to: '5', satisfied: true },
      { from: '2', to: '4', satisfied: true },
      { from: '3', to: '6', satisfied: false },
      { from: '4', to: '6', satisfied: false },
      { from: '6', to: '7', satisfied: false },
    ],
  };
}
