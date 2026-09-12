export type StepperCondition = 'PENDING' | 'REACHABLE' | 'ASSIGNED' | 'CLOSED';

export interface StepperSegment {
  condition: StepperCondition;

  title?: string;
}

export function segmentsFromProgress(closed: number, total: number): readonly StepperSegment[] {
  return Array.from({ length: Math.max(total, 0) }, (_unused, index) => ({
    condition: index < closed ? ('CLOSED' as const) : ('PENDING' as const),
  }));
}
