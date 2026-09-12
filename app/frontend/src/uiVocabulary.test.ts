import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const FORBIDDEN = /\bbrackets?\b/i;

const SRC = fileURLToPath(new URL('.', import.meta.url));

const RULE =
  'R21.9 — no UI string may contain the word "bracket". Say "work", "this piece of work", or the piece\'s own name.';

describe('the interface never says "bracket"', () => {
  it('has no locale string carrying it', () => {
    const offenders: string[] = [];

    for (const file of filesUnder(join(SRC, 'i18n', 'locales'), ['.json'])) {
      walkValues(JSON.parse(readFileSync(file, 'utf8')) as unknown, (path, value) => {
        if (FORBIDDEN.test(value)) {
          offenders.push(`${path} = ${JSON.stringify(value)}`);
        }
      });
    }

    expect(offenders, RULE).toEqual([]);
  });

  it('has no rendered text carrying it', () => {
    const offenders: string[] = [];

    for (const file of filesUnder(join(SRC, 'features'), ['.tsx'])) {
      if (file.includes('.test.')) {
        continue;
      }

      for (const text of jsxTextIn(withoutComments(readFileSync(file, 'utf8')))) {
        if (FORBIDDEN.test(text)) {
          offenders.push(`${file.slice(SRC.length)} — ${JSON.stringify(text.trim())}`);
        }
      }
    }

    expect(offenders, RULE).toEqual([]);
  });

  it('has no string in a JSX expression carrying it', () => {
    const offenders: string[] = [];

    for (const file of filesUnder(join(SRC, 'features'), ['.tsx'])) {
      if (file.includes('.test.')) {
        continue;
      }

      for (const literal of literalsIn(withoutComments(readFileSync(file, 'utf8')))) {
        if (looksLikeProse(literal) && FORBIDDEN.test(literal)) {
          offenders.push(`${file.slice(SRC.length)} — ${JSON.stringify(literal)}`);
        }
      }
    }

    expect(offenders, RULE).toEqual([]);
  });
});

function literalsIn(source: string): string[] {
  const found: string[] = [];

  for (const match of source.matchAll(
    /'([^'\n\\]{2,200})'|"([^"\n\\]{2,200})"|`([^`\\]{2,200})`/g,
  )) {
    const literal = match[1] ?? match[2] ?? match[3];

    if (literal !== undefined) {
      found.push(literal);
    }
  }

  return found;
}

function looksLikeProse(text: string): boolean {
  return text.includes(' ') && !text.startsWith('/');
}

function filesUnder(dir: string, extensions: readonly string[]): string[] {
  const out: string[] = [];

  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);

    if (statSync(full).isDirectory()) {
      out.push(...filesUnder(full, extensions));
    } else if (extensions.some((extension) => entry.endsWith(extension))) {
      out.push(full);
    }
  }

  return out;
}

function walkValues(node: unknown, visit: (path: string, value: string) => void, at = ''): void {
  if (typeof node === 'string') {
    visit(at, node);
    return;
  }

  if (node !== null && typeof node === 'object') {
    for (const [key, value] of Object.entries(node)) {
      walkValues(value, visit, at === '' ? key : `${at}.${key}`);
    }
  }
}

function withoutComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

function jsxTextIn(source: string): string[] {
  const found: string[] = [];

  for (const match of source.matchAll(/>([^<>{}]+)</g)) {
    const text = match[1];
    if (text !== undefined && text.trim() !== '' && !looksLikeCode(text)) {
      found.push(text);
    }
  }

  return found;
}

function looksLikeCode(text: string): boolean {
  return /[;=()]/.test(text);
}
