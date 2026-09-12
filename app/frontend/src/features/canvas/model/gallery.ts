import type { Translate } from './layout';
import type { CanvasNode } from './node';

const HOUR = 60 * 60;

export const GALLERY_NOW = new Date('2026-08-13T09:00:00Z');

export function galleryEntries(t: Translate): ReadonlyArray<{ note: string; node: CanvasNode }> {
  const entry = (id: string, node: Omit<CanvasNode, 'id' | 'title'>) => ({
    note: t(`canvas.gallery.notes.${id}`),
    node: { ...node, id, title: t(`canvas.gallery.titles.${id}`) },
  });

  return [
    entry('closed', {
      condition: 'Closed',
      stepNumber: 1,
      taskState: 'Closed',
      assignee: { name: 'Maria Pop' },
      phases: [
        { phase: 'active', seconds: 6 * HOUR },
        { phase: 'wait', seconds: 22 * HOUR },
      ],
    }),
    entry('created', {
      condition: 'Assigned',
      stepNumber: 2,
      taskState: 'Created',
      assignee: { name: 'Radu Cîmpean' },
      dueAt: '2026-08-20T15:00:00Z',
      meta: [{ label: t('canvas.node.seen'), value: t('canvas.node.notYet') }],
    }),
    entry('in-progress', {
      condition: 'Assigned',
      stepNumber: 3,
      taskState: 'InProgress',
      assignee: { name: 'Ana Neagu' },
      dueAt: '2026-08-18T15:00:00Z',
      phases: [
        { phase: 'active', seconds: 3 * HOUR },
        { phase: 'wait', seconds: 5 * HOUR },
      ],
    }),
    entry('at-risk', {
      condition: 'Assigned',
      stepNumber: 3,
      taskState: 'InProgress',
      assignee: { name: 'Ana Neagu' },
      dueAt: '2026-08-15T15:00:00Z',
      atRisk: true,
      phases: [{ phase: 'active', seconds: 4 * HOUR }],
    }),
    entry('blocked', {
      condition: 'Assigned',
      stepNumber: 4,
      taskState: 'Blocked',
      assignee: { name: 'Dan Stan' },
      dueAt: '2026-08-19T15:00:00Z',
      blockedReason: t('canvas.gallery.reasons.licence'),
      phases: [
        { phase: 'active', seconds: 2 * HOUR },
        { phase: 'blocked', seconds: 72 * HOUR },
      ],
    }),
    entry('overdue', {
      condition: 'Assigned',
      stepNumber: 5,
      taskState: 'InProgress',
      assignee: { name: 'Ioana Marinescu' },
      dueAt: '2026-08-11T15:00:00Z',
      phases: [{ phase: 'active', seconds: 5 * HOUR }],
    }),
    entry('blocked-and-overdue', {
      condition: 'Assigned',
      stepNumber: 6,
      taskState: 'Blocked',
      assignee: { name: 'Dan Stan' },
      dueAt: '2026-08-10T15:00:00Z',
      blockedReason: t('canvas.gallery.reasons.bank'),
      phases: [
        { phase: 'active', seconds: 1 * HOUR },
        { phase: 'blocked', seconds: 96 * HOUR },
      ],
    }),
    entry('in-review', {
      condition: 'Assigned',
      stepNumber: 7,
      taskState: 'Completed',
      assignee: { name: 'Mihai Vasilescu' },
      phases: [
        { phase: 'active', seconds: 7 * HOUR },
        { phase: 'review', seconds: 48 * HOUR },
      ],
    }),
    entry('sent-back', {
      condition: 'Assigned',
      stepNumber: 7,
      taskState: 'InProgress',
      assignee: { name: 'Mihai Vasilescu' },
      reworkNote: t('canvas.gallery.reasons.rework'),
      phases: [
        { phase: 'active', seconds: 7 * HOUR },
        { phase: 'review', seconds: 48 * HOUR },
      ],
    }),
    entry('ready', {
      condition: 'Reachable',
      stepNumber: 8,
      meta: [
        { label: t('canvas.node.unlocked'), value: t('canvas.gallery.unlockedBy') },
        { label: t('canvas.node.waiting'), value: t('canvas.gallery.waitedDays'), tone: 'warn' },
      ],
      offersAssignment: true,
    }),
    entry('pending', {
      condition: 'Pending',
      stepNumber: 9,
      meta: [{ label: t('canvas.node.waitsOn'), value: t('canvas.gallery.waitsOnSteps') }],
    }),
    entry('former-member', {
      condition: 'Assigned',
      stepNumber: 10,
      taskState: 'InProgress',
      assignee: { name: null, erased: true },
      phases: [{ phase: 'active', seconds: 2 * HOUR }],
    }),
  ];
}
