import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { DURATION, EASE, STAGGER_CAP, STAGGER_SECONDS, staggerFor } from './tokens';

const stylesheet = readFileSync(join(process.cwd(), 'src/index.css'), 'utf8');

function customProperty(name: string): string {
  const found = new RegExp(`--${name}:\\s*([^;]+);`).exec(stylesheet);
  const value = found?.[1];
  if (value === undefined) {
    throw new Error(`index.css has no --${name}`);
  }
  return value.trim();
}

describe('the motion tokens are the stylesheet’s, restated for the library', () => {
  it.each([
    ['duration-state', DURATION.state],
    ['duration-reveal', DURATION.reveal],
    ['duration-expand', DURATION.expand],
    ['duration-transition', DURATION.transition],
    ['duration-data', DURATION.data],
  ])('%s matches', (property, seconds) => {
    expect(customProperty(property)).toBe(`${String(Math.round(seconds * 1000))}ms`);
  });

  it.each([
    ['ease-standard', EASE.standard],
    ['ease-exit', EASE.exit],
    ['ease-spatial', EASE.spatial],
  ])('%s matches', (property, curve) => {
    expect(customProperty(property)).toBe(`cubic-bezier(${curve.join(', ')})`);
  });

  it('stagger matches', () => {
    expect(customProperty('stagger')).toBe(`${String(STAGGER_SECONDS * 1000)}ms`);
  });
});

describe('the stagger stops climbing', () => {
  it('adds one step per item up to the cap', () => {
    expect(staggerFor(0)).toBeCloseTo(0);
    expect(staggerFor(3)).toBeCloseTo(3 * STAGGER_SECONDS);
    expect(staggerFor(STAGGER_CAP)).toBeCloseTo(STAGGER_CAP * STAGGER_SECONDS);
  });

  it('holds the last item’s delay beyond the cap, so a long list never cascades', () => {
    expect(staggerFor(40)).toBeCloseTo(staggerFor(STAGGER_CAP));
    expect(staggerFor(400)).toBeCloseTo(STAGGER_CAP * STAGGER_SECONDS);
  });
});
