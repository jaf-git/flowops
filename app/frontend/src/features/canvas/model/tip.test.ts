import { describe, expect, it } from 'vitest';

import type { BandCard } from './bands';
import { anchorTip } from './tip';

const card = {
  id: 'step',
  title: 'Send the quote to Atelier Lemn',
  taskId: null,
  state: 'notStarted',
  blockedReason: null,
  taskState: null,
  bottleneckMinutes: null,
  description: null,
  assigneeId: null,
  assigneeName: null,
  deadline: null,
  atRisk: false,
  dependsOn: [],
} satisfies BandCard;

describe('where the hover card goes', () => {
  it('centres on the card it points at', () => {
    const tip = anchorTip(card, { top: 400, bottom: 496, left: 200, width: 176 });

    expect(tip.x).toBe(288);
  });

  it('sits above a card with room above it', () => {
    const tip = anchorTip(card, { top: 400, bottom: 496, left: 200, width: 176 });

    expect(tip.below).toBe(false);
    expect(tip.y).toBeLessThan(400);
  });

  it('flips beneath a card too near the top of the window', () => {
    const tip = anchorTip(card, { top: 60, bottom: 156, left: 200, width: 176 });

    expect(tip.below).toBe(true);
    expect(tip.y).toBeGreaterThan(156);
  });
});
