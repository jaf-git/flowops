import { vi } from 'vitest';

import type { InsightDecision } from '../hooks/useInsightDecision';

export function aDecision(over: Partial<InsightDecision> = {}): InsightDecision {
  return {
    apply: vi.fn(),
    dismiss: vi.fn(),
    deciding: null,
    refusalFor: () => null,
    ...over,
  };
}
