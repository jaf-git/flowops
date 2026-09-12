import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const SOURCE = join(__dirname);

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      return sourceFiles(path);
    }
    const styled = /\.(css|ts|tsx)$/.test(entry) && !entry.includes('.test.');
    return styled ? [path] : [];
  });
}

describe('css custom properties', () => {
  it('resolve to a token that exists, or carry a fallback', { timeout: 20_000 }, () => {
    const files = sourceFiles(SOURCE);
    const contents = new Map(files.map((path) => [path, readFileSync(path, 'utf8')]));

    const defined = new Set<string>();
    for (const text of contents.values()) {
      for (const match of text.matchAll(/^\s*(--[a-zA-Z0-9-]+)\s*:/gm)) {
        if (match[1] !== undefined) {
          defined.add(match[1]);
        }
      }
    }

    for (const text of contents.values()) {
      for (const match of text.matchAll(/['"](--[a-zA-Z0-9-]+)['"]\s*:/g)) {
        if (match[1] !== undefined) {
          defined.add(match[1]);
        }
      }
    }

    const unresolved: string[] = [];
    for (const [path, text] of contents) {
      for (const match of text.matchAll(/var\(\s*(--[a-zA-Z0-9-]+)\s*\)/g)) {
        const token = match[1];
        if (token !== undefined && !defined.has(token)) {
          unresolved.push(`${token} in ${path.replace(SOURCE, 'src')}`);
        }
      }
    }

    expect(
      unresolved,
      `These resolve to nothing, so the declaration is dropped and the property inherits instead — ` +
        `which looks like a working screen. Define the token, or give the var() a fallback.\n` +
        unresolved.join('\n'),
    ).toEqual([]);
  });
});
