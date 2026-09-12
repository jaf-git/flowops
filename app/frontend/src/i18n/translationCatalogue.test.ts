import { describe, expect, it } from 'vitest';

import en from './locales/en/common.json';

type Translations = Record<string, unknown>;

function keysOf(translations: Translations, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => {
    const path = prefix === '' ? key : `${prefix}.${key}`;
    return typeof value === 'object' && value !== null
      ? keysOf(value as Translations, path)
      : [path];
  });
}

function family(key: string): string {
  return key.replace(/_(zero|one|two|few|many|other)$/, '');
}

describe('the translation catalogue', () => {
  it('gives every pluralised message an "other" form', () => {
    const pluralised = new Set(
      keysOf(en)
        .filter((key) => key !== family(key))
        .map(family),
    );

    for (const base of pluralised) {
      expect(keysOf(en), base).toContain(`${base}_other`);
    }
  });

  it('leaves no value empty', () => {
    expect(keysOf(en).filter((path) => valueAt(en, path).trim() === '')).toEqual([]);
  });

  it('names every password rule the backend can report', () => {
    const rules = [
      'MINIMUM_LENGTH',
      'MAXIMUM_LENGTH',
      'NOT_EMAIL_LOCAL_PART',
      'NOT_CURRENT_PASSWORD',
      'UNKNOWN',
    ];

    for (const rule of rules) {
      expect(keysOf(en)).toContain(`auth.error.passwordRule.${rule}`);
    }
  });
});

function valueAt(translations: Translations, path: string): string {
  return path
    .split('.')
    .reduce<unknown>((value, segment) => (value as Translations)[segment], translations) as string;
}
