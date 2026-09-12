import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const SRC = fileURLToPath(new URL('..', import.meta.url));
const CATALOGUE = JSON.parse(
  readFileSync(join(SRC, 'i18n', 'locales', 'en', 'common.json'), 'utf8'),
) as Record<string, unknown>;

const PLURAL_SUFFIXES = ['', '_zero', '_one', '_two', '_few', '_many', '_other'];

function valueAt(key: string): unknown {
  return key
    .split('.')
    .reduce<unknown>(
      (node, part) =>
        node === undefined || node === null ? undefined : (node as Record<string, unknown>)[part],
      CATALOGUE,
    );
}

const exists = (key: string): boolean =>
  PLURAL_SUFFIXES.some((suffix) => valueAt(key + suffix) !== undefined);

function sourceFiles(directory: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(directory)) {
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) {
      found.push(...sourceFiles(full));
      continue;
    }
    if (/\.(ts|tsx)$/.test(full) && !/\.test\./.test(full)) {
      found.push(full);
    }
  }
  return found;
}

describe('the interface never renders a translation key', () => {
  it('resolves every literal key it asks for', () => {
    const missing: string[] = [];

    for (const file of sourceFiles(SRC)) {
      const text = readFileSync(file, 'utf8');
      for (const [, key] of text.matchAll(/\bt\(\s*'([a-zA-Z0-9_.]+)'/g)) {
        if (key !== undefined && !exists(key)) {
          missing.push(`${key}  —  ${file.slice(SRC.length)}`);
        }
      }
    }

    expect(
      [...new Set(missing)].sort(),
      'A key with no string renders as itself on screen. Add it to en/common.json.',
    ).toEqual([]);
  });
});
