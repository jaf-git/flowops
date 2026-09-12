import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const VARIANTS = ['primary', 'accent', 'secondary', 'quiet', 'tertiary', 'destructive'] as const;

const SOURCE = fileURLToPath(new URL('../..', import.meta.url));

describe('the button classes', () => {
  const offenders = { variantWithoutBase: [] as string[], baseWithoutVariant: [] as string[] };

  for (const file of sourceFiles(SOURCE)) {
    const text = readFileSync(file, 'utf8');
    for (const [index, line] of text.split('\n').entries()) {
      if (!line.includes('className=')) {
        continue;
      }
      const where = `${file.slice(SOURCE.length)}:${String(index + 1)}`;
      const wearsVariant = VARIANTS.some((variant) => line.includes(`ui-button-${variant}`));
      const wearsBase = /ui-button(\s|"|`|'|\})/.test(line);

      if (wearsVariant && !wearsBase) {
        offenders.variantWithoutBase.push(`${where} — ${line.trim()}`);
      }

      if (wearsBase && !wearsVariant && /className="ui-button"/.test(line)) {
        offenders.baseWithoutVariant.push(`${where} — ${line.trim()}`);
      }
    }
  }

  it('never wears a variant without the geometry the base class carries', () => {
    expect(offenders.variantWithoutBase).toEqual([]);
  });

  it('never wears the base class without a variant, which would render with no fill', () => {
    expect(offenders.baseWithoutVariant).toEqual([]);
  });
});

describe('the button geometry', () => {
  const BUTTON = ruleBodyOf('.ui-button');

  it('floors its height rather than fixing it, so a two-line label has somewhere to go', () => {
    expect(BUTTON, 'a `min-height` floor').toMatch(/min-height:\s*\d+/);
    expect(BUTTON, '`height: auto`, so the floor is a floor and not a ceiling').toMatch(
      /height:\s*auto/,
    );

    expect(BUTTON).not.toMatch(/(?<!min-)height:\s*\d+px/);
  });

  it('lets a label wrap instead of clipping it', () => {
    expect(BUTTON).toMatch(/white-space:\s*normal/);
  });

  it('pads on the block axis, which is what a second line grows into', () => {
    expect(BUTTON).toMatch(/padding:\s*(?!0)[^;]+\s+[^;]+;/);
  });
});

function ruleBodyOf(selector: string): string {
  const css = readFileSync(join(SOURCE, 'index.css'), 'utf8');
  const start = css.indexOf(`
${selector} {`);
  expect(start, `${selector} is declared in index.css`).toBeGreaterThan(-1);
  return css.slice(start, css.indexOf('}', start));
}

function sourceFiles(directory: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(directory)) {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) {
      found.push(...sourceFiles(path));
    } else if (path.endsWith('.tsx')) {
      found.push(path);
    }
  }
  return found;
}
