export const DURATION = {
  state: 0.12,
  reveal: 0.18,
  expand: 0.24,
  transition: 0.32,
  data: 0.48,
} as const;

export const EASE = {
  standard: [0.2, 0.8, 0.2, 1],
  exit: [0.4, 0, 1, 1],
  spatial: [0.16, 1, 0.3, 1],
} as const;

export const STAGGER_SECONDS = 0.024;

export const STAGGER_CAP = 8;

export function staggerFor(index: number): number {
  return Math.min(index, STAGGER_CAP) * STAGGER_SECONDS;
}

export const RISE_PX = 8;
