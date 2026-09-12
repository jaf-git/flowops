import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

type Role =
  | { kind: 'surface' }
  | { kind: 'text' }
  | { kind: 'chrome' }
  | { kind: 'spatial-surface' }
  | { kind: 'spatial-text' }
  | { kind: 'fill'; measuredBy: string }
  | { kind: 'exempt'; because: string };

const SURFACES = [
  'surface-sunken',
  'surface-canvas',
  'surface-inset',
  'surface-raised',
  'surface-spatial',
] as const;

const SPATIAL_SURFACES = [
  'spatial-void',
  'spatial-inset',
  'spatial-chrome',
  'spatial-node',
  'spatial-raised',
] as const;

const ROLES: Record<string, Role> = {
  'surface-sunken': { kind: 'surface' },
  'surface-canvas': { kind: 'surface' },
  'surface-inset': { kind: 'surface' },
  'surface-raised': { kind: 'surface' },
  'surface-spatial': { kind: 'surface' },

  'spatial-void': { kind: 'spatial-surface' },
  'spatial-inset': { kind: 'spatial-surface' },
  'spatial-chrome': { kind: 'spatial-surface' },
  'spatial-node': { kind: 'spatial-surface' },
  'spatial-raised': { kind: 'spatial-surface' },

  'spatial-ink-900': { kind: 'spatial-text' },
  'spatial-ink-700': { kind: 'spatial-text' },
  'spatial-ink-500': { kind: 'spatial-text' },
  'spatial-ink-400': { kind: 'spatial-text' },

  'ink-900': { kind: 'text' },
  'ink-800': { kind: 'text' },
  'ink-700': { kind: 'text' },
  'ink-500': { kind: 'text' },
  'ink-400': { kind: 'text' },

  'focus-ring': { kind: 'chrome' },
  'critical-line': { kind: 'chrome' },
  'volt-800': { kind: 'chrome' },

  'surface-emphasis': { kind: 'fill', measuredBy: 'on-ink on surface-emphasis' },
  'on-ink': { kind: 'fill', measuredBy: 'on-ink on surface-emphasis' },

  'volt-100': { kind: 'fill', measuredBy: 'ink-900 on volt-100' },
  'volt-300': { kind: 'fill', measuredBy: 'ink-900 on volt-300' },
  'volt-500': { kind: 'fill', measuredBy: 'ink-900 on volt-500' },
  'volt-600': { kind: 'fill', measuredBy: 'ink-900 on volt-600' },

  positive: { kind: 'fill', measuredBy: 'on-positive on positive' },
  'on-positive': { kind: 'fill', measuredBy: 'on-positive on positive' },
  info: { kind: 'fill', measuredBy: 'on-info on info' },
  'on-info': { kind: 'fill', measuredBy: 'on-info on info' },
  warning: { kind: 'fill', measuredBy: 'on-warning on warning' },
  'on-warning': { kind: 'fill', measuredBy: 'on-warning on warning' },
  critical: { kind: 'fill', measuredBy: 'on-critical on critical' },
  'on-critical': { kind: 'fill', measuredBy: 'on-critical on critical' },

  'data-1': { kind: 'fill', measuredBy: 'ink-900 on data-1' },
  'data-2': { kind: 'fill', measuredBy: 'ink-900 on data-2' },
  'data-3': { kind: 'fill', measuredBy: 'ink-900 on data-3' },
  'data-4': { kind: 'fill', measuredBy: 'ink-900 on data-4' },
  'data-5': { kind: 'fill', measuredBy: 'ink-900 on data-5' },
  'data-6': { kind: 'fill', measuredBy: 'ink-900 on data-6' },

  'person-1': { kind: 'fill', measuredBy: 'on-ink on person-1' },
  'person-2': { kind: 'fill', measuredBy: 'on-ink on person-2' },
  'person-3': { kind: 'fill', measuredBy: 'on-ink on person-3' },
  'person-4': { kind: 'fill', measuredBy: 'on-ink on person-4' },
  'person-5': { kind: 'fill', measuredBy: 'on-ink on person-5' },
  'person-6': { kind: 'fill', measuredBy: 'on-ink on person-6' },

  'ink-200': {
    kind: 'exempt',
    because:
      'Disabled text and hairlines, and below the text floor on purpose. Disabled means "not available to be read or used", and a disabled control that meets the same floor as an enabled one has failed to say so. It is also the quietest separator in a system whose containment is read from the surface ladder rather than from borders (§2), so it bounds nothing that is not already distinct. It must never carry a sentence a person is expected to read, and it is a row here rather than an omission so that the day somebody reaches for it as body text this file makes them justify it. Where it does carry text — on the emphasis panel, as the quieter of that panel two strings — it is measured in PAIRS.',
  },
  'focus-bloom': {
    kind: 'exempt',
    because:
      'The outer half of the focus ring, and decoration by construction. Volt draws focus as a lime bloom alone, which measures 1.01:1 against a white card; the ring therefore carries a 2px --focus-ring inner edge that meets 3:1 on every surface, and this token is the glow outside it. It claims no floor because it is never the only thing drawn — if it ever becomes the only thing drawn, this row is wrong and the ring is broken.',
  },
  'canvas-grid': {
    kind: 'exempt',
    because:
      'Texture rather than interface. Dots on the spatial plane whose only job is to make panning read as movement rather than as content sliding for no reason; it bounds nothing, sits behind no text, and carries no state. A row rather than an omission, so that the day somebody reaches for it as a border this file makes them justify it.',
  },
  'spatial-grid': {
    kind: 'exempt',
    because:
      'The dark plane dot grid — --canvas-grid on the other ladder, and exempt for the same reason: texture, behind no text, bounding nothing.',
  },
  'plane-grid': {
    kind: 'exempt',
    because:
      'The same dot grid again, under the name the graph reads it by — texture, behind no text, bounding nothing, exempt for the third time for the one reason. It is its own token rather than --canvas-grid because the light canvas sits on the cool --surface-spatial ground, and the neutral grey grid reads as dirt on it; it is re-pointed to --spatial-grid inside .fo-spatial so the plane names its grid once.',
  },
  'spatial-ink-200': {
    kind: 'exempt',
    because:
      'Disabled text and separators on dark, and below the floor on purpose for exactly the reason --ink-200 is: disabled means "not available to be read or used", and a disabled control meeting an enabled one\'s floor has failed to say so. It must never carry a sentence somebody is expected to read.',
  },
  'spatial-edge': {
    kind: 'exempt',
    because:
      'The hairline on floating chrome, and the one place the dark ladder needs help the light one does not: on a dark ground the surface steps compress to almost nothing, so --spatial-chrome on --spatial-void is a 1.1:1 step and the hairline is what says *this floats*. §5 already sanctions it as part of the Glass material rather than as a border, and it bounds no control on its own — the toolbar it edges is hit-tested by its whole area and reachable by keyboard through the ring, not through this. A row rather than an omission, so the day somebody reaches for it as a control boundary this file makes them justify it.',
  },
};

const PAIRS: ReadonlyArray<{ on: string; of: string; floor: number }> = [
  { of: 'on-ink', on: 'surface-emphasis', floor: 4.5 },
  { of: 'ink-200', on: 'surface-emphasis', floor: 4.5 },

  { of: 'ink-900', on: 'volt-100', floor: 4.5 },
  { of: 'ink-900', on: 'volt-300', floor: 4.5 },
  { of: 'ink-900', on: 'volt-500', floor: 4.5 },
  { of: 'ink-900', on: 'volt-600', floor: 4.5 },

  { of: 'on-positive', on: 'positive', floor: 4.5 },
  { of: 'on-info', on: 'info', floor: 4.5 },
  { of: 'on-warning', on: 'warning', floor: 4.5 },
  { of: 'on-critical', on: 'critical', floor: 4.5 },

  { of: 'ink-900', on: 'data-1', floor: 4.5 },
  { of: 'ink-900', on: 'data-2', floor: 4.5 },
  { of: 'ink-900', on: 'data-3', floor: 4.5 },
  { of: 'ink-900', on: 'data-4', floor: 4.5 },
  { of: 'ink-900', on: 'data-5', floor: 4.5 },
  { of: 'ink-900', on: 'data-6', floor: 4.5 },

  { of: 'on-ink', on: 'person-1', floor: 4.5 },
  { of: 'on-ink', on: 'person-2', floor: 4.5 },
  { of: 'on-ink', on: 'person-3', floor: 4.5 },
  { of: 'on-ink', on: 'person-4', floor: 4.5 },
  { of: 'on-ink', on: 'person-5', floor: 4.5 },
  { of: 'on-ink', on: 'person-6', floor: 4.5 },

  { of: 'data-1', on: 'spatial-node', floor: 3 },
  { of: 'data-2', on: 'spatial-node', floor: 3 },
  { of: 'data-3', on: 'spatial-node', floor: 3 },
  { of: 'data-4', on: 'spatial-node', floor: 3 },
  { of: 'data-5', on: 'spatial-node', floor: 3 },
  { of: 'data-6', on: 'spatial-node', floor: 3 },

  { of: 'volt-500', on: 'spatial-void', floor: 3 },
  { of: 'spatial-void', on: 'volt-500', floor: 4.5 },
];

const FLOOR = { text: 4.5, chrome: 3 } as const;

const TOKENS = readColourTokens();

describe('the Volt colour tokens', () => {
  it('are each classified, and nothing is classified that is not a token', () => {
    expect(Object.keys(TOKENS).sort()).toEqual(Object.keys(ROLES).sort());
  });

  it('reads a full token set rather than an accidentally empty one', () => {
    expect(Object.keys(TOKENS).length).toBeGreaterThanOrEqual(30);
  });

  for (const [name, role] of Object.entries(ROLES)) {
    if (role.kind !== 'text' && role.kind !== 'chrome') {
      continue;
    }

    it(`--${name} meets ${FLOOR[role.kind]}:1 on every surface`, () => {
      for (const surface of SURFACES) {
        const measured = contrast(token(name), token(surface));

        expect(
          measured,
          `--${name} on --${surface} is ${measured}:1, below the ${FLOOR[role.kind]}:1 floor`,
        ).toBeGreaterThanOrEqual(FLOOR[role.kind]);
      }
    });
  }

  for (const [name, role] of Object.entries(ROLES)) {
    if (role.kind !== 'spatial-text') {
      continue;
    }

    it(`--${name} meets ${FLOOR.text}:1 on every spatial ground`, () => {
      for (const surface of SPATIAL_SURFACES) {
        const measured = contrast(token(name), token(surface));

        expect(
          measured,
          `--${name} on --${surface} is ${measured}:1, below the ${FLOOR.text}:1 floor`,
        ).toBeGreaterThanOrEqual(FLOOR.text);
      }
    });
  }

  for (const pair of PAIRS) {
    it(`--${pair.of} meets ${pair.floor}:1 on --${pair.on}`, () => {
      const measured = contrast(token(pair.of), token(pair.on));

      expect(measured, `--${pair.of} on --${pair.on} is ${measured}:1`).toBeGreaterThanOrEqual(
        pair.floor,
      );
    });
  }
});

function token(name: string): string {
  const value = TOKENS[name];

  if (value === undefined) {
    throw new Error(`no --${name} in index.css`);
  }

  return value;
}

function readColourTokens(): Record<string, string> {
  const stylesheet = readFileSync(fileURLToPath(new URL('./index.css', import.meta.url)), 'utf8');

  const declarations = [...stylesheet.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)];

  return Object.fromEntries(declarations.map((match) => [match[1], match[2]]));
}

function luminance(hex: string): number {
  const channel = (offset: number): number => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  };

  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

function contrast(foreground: string, background: string): number {
  const one = luminance(foreground);
  const other = luminance(background);

  return Math.round(((Math.max(one, other) + 0.05) / (Math.min(one, other) + 0.05)) * 100) / 100;
}
