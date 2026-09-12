import { describe, expect, it } from 'vitest';

import { adoptionShare } from './adoptionShare';

describe('the adoption share printed beside the two counts it comes from', () => {
  it('rounds the ordinary case', () => {
    expect(adoptionShare(43, 50)).toBe('86');
    expect(adoptionShare(497, 497)).toBe('100');
  });

  it('never prints 0% beside a count that is not zero', () => {
    // 2 of 497 rounds to 0, and "0% — 2 of 497" reads as a contradiction in one sentence.
    expect(adoptionShare(2, 497)).toBe('<1');
  });

  it('prints 0 when nobody named one', () => {
    expect(adoptionShare(0, 497)).toBe('0');
  });
});
