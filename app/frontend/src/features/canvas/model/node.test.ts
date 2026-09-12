import { describe, expect, it } from 'vitest';

import { borderTreatmentOf, type CanvasNode } from './node';

const held: CanvasNode = {
  id: 'step-4',
  title: 'Create IT accounts',
  condition: 'Assigned',
  taskState: 'InProgress',
};

describe('what a node’s border reports', () => {
  it('says overdue rather than blocked when the work is both', () => {
    const treatment = borderTreatmentOf(
      { ...held, taskState: 'Blocked', blockedReason: 'Waiting on the supplier' },
      { overdue: true },
    );

    expect(treatment).toBe('overdue');
  });

  it('says overdue rather than selected, because a click may not hide a fact', () => {
    expect(borderTreatmentOf(held, { overdue: true, selected: true })).toBe('overdue');
  });

  it('says blocked rather than selected, for the same reason', () => {
    const blocked: CanvasNode = { ...held, taskState: 'Blocked', blockedReason: 'Supplier' };

    expect(borderTreatmentOf(blocked, { selected: true })).toBe('blocked');
  });

  it('marks a reachable step nobody holds, because that is the stall', () => {
    const reachable: CanvasNode = { id: 'step-5', title: 'Payroll', condition: 'Reachable' };

    expect(borderTreatmentOf(reachable)).toBe('ready');
  });

  it('keeps the stall’s own mark when it is selected, so clicking it hides nothing', () => {
    const reachable: CanvasNode = { id: 'step-5', title: 'Payroll', condition: 'Reachable' };

    expect(borderTreatmentOf(reachable, { selected: true })).toBe('ready');
  });

  it('marks a step nobody can start yet as pending, and only when nothing else applies', () => {
    const pending: CanvasNode = { id: 'step-6', title: 'Briefing', condition: 'Pending' };

    expect(borderTreatmentOf(pending)).toBe('pending');
    expect(borderTreatmentOf(pending, { selected: true })).toBe('selected');
  });

  it('reports selection when there is nothing about the work to report instead', () => {
    expect(borderTreatmentOf(held, { selected: true })).toBe('selected');
  });

  it('reports nothing about work that is simply proceeding', () => {
    expect(borderTreatmentOf(held)).toBe('default');
  });

  it('treats a closed step as ordinary, because finished work is not urgent', () => {
    const closed: CanvasNode = { ...held, condition: 'Closed', taskState: 'Closed' };

    expect(borderTreatmentOf(closed)).toBe('default');
  });
});
