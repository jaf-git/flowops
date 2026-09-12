import { describe, expect, it } from 'vitest';

import en from '../../i18n/locales/en/common.json';
import { dismissalFor } from './dismissalWording';

describe('what the dismiss button says', () => {
  it('offers a judgement on the finding a merge raises, not a deferral', () => {
    expect(dismissalFor('templates_left_by_a_merge')).toBe('findings.stillCorrect');
  });

  it('offers a deferral on everything else', () => {
    expect(dismissalFor('waiting_on_one_person')).toBe('findings.notNow');
    expect(dismissalFor('')).toBe('findings.notNow');
  });

  it('names keys that exist, because a missing key renders as the key itself', () => {
    expect(en.findings.stillCorrect).toBeTruthy();
    expect(en.findings.notNow).toBeTruthy();
  });
});
