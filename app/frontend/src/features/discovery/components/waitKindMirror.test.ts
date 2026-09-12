import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../../../../../..');
const WAIT_KIND = resolve(
  ROOT,
  'app/backend/src/main/java/com/flowops/discovery/domain/enums/WaitKind.java',
);

const MIRRORED: Readonly<Record<string, number>> = {
  CLIENT: 5,
  SUPPLIER: 5,
  COLLEAGUE: 2,
  APPROVAL: 2,
};

describe('the wait kinds the form mirrors', () => {
  const java = readFileSync(WAIT_KIND, 'utf8');

  const declared = new Map<string, number>();
  for (const match of java.matchAll(/^\s*([A-Z_]+)\(Duration\.ofDays\((\d+)\)\)/gm)) {
    const name = match[1];
    const days = match[2];

    if (name !== undefined && days !== undefined) {
      declared.set(name, Number(days));
    }
  }

  it('names every kind the enum declares, and no others', () => {
    expect([...declared.keys()].sort()).toEqual(Object.keys(MIRRORED).sort());
  });

  it.each(Object.entries(MIRRORED))(
    'suggests %s the same span the server assumes',
    (kind, days) => {
      expect(declared.get(kind)).toBe(days);
    },
  );
});
