import { describe, expect, it } from 'vitest';

import { durationLabel, relativeLabel } from './elapsed';

const NOW = new Date('2026-08-03T12:00:00Z');

describe('how long ago something was', () => {
  it('truncates towards zero rather than rounding away from it', () => {
    expect(relativeLabel(new Date('2026-08-01T13:00:00Z'), NOW, 'en')).toBe('yesterday');
  });

  it('reads the future as the future', () => {
    expect(relativeLabel(new Date('2026-08-06T12:00:00Z'), NOW, 'en')).toBe('in 3 days');
  });

  it('delegates word order to Intl, which English alone cannot prove', () => {
    const romanian = relativeLabel(new Date('2026-07-31T12:00:00Z'), NOW, 'ro');

    expect(romanian).toContain('zile');
    expect(romanian.startsWith('acum')).toBe(true);
  });
});

describe('a length of time', () => {
  it('picks the largest unit that fits', () => {
    expect(durationLabel(4 * 60 * 60, 'en')).toBe('4h');
    expect(durationLabel(2 * 24 * 60 * 60, 'en')).toBe('2d');
    expect(durationLabel(30 * 60, 'en')).toBe('30m');
  });

  it('keeps one decimal rather than swallowing half of it', () => {
    expect(durationLabel(90 * 60, 'en')).toBe('1.5h');
  });

  it('never reports a real span as nothing', () => {
    expect(durationLabel(40, 'en')).not.toBe('0m');
  });

  it('reports genuine nothing as nothing', () => {
    expect(durationLabel(0, 'en')).toBe('0m');
  });
});
