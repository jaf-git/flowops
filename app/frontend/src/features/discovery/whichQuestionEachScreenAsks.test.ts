import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

const CALLERS: Record<string, { question: 'offering' | 'describing'; why: string }> = {
  'components/WorkCircles.tsx': {
    question: 'offering',
    why: 'The strip on a message asks where a new mark may go, and a finished engagement is not somewhere to put work.',
  },
  'routes/CanvasScreen.tsx': {
    question: 'describing',
    why: 'The canvas draws what the business did. A finished engagement is the thing most worth drawing.',
  },
  'routes/JobGraphScreen.tsx': {
    question: 'describing',
    why: 'One engagement, drawn. It asked the offering question and said "Nothing is open anywhere" over fifty closed ones.',
  },
  'components/ConversationInspector.tsx': {
    question: 'describing',
    why: 'The first victim of this defect, and already fixed: closing an engagement emptied its list.',
  },
  'hooks/useBracketReach.ts': {
    question: 'describing',
    why: 'A reach map over open engagements alone reports "this work is only here" for every bracket in the others — the exact false sentence the hook exists to prevent.',
  },
  'hooks/useWorkVocabulary.ts': {
    question: 'describing',
    why: 'The words the business uses are counted from every engagement. Over open ones alone the near-duplicate control goes quiet.',
  },
};

const ROOT = join(process.cwd(), 'src', 'features', 'discovery');

const CALL = /(?:useJobsForConversation|fetchJobsForConversation)\(([^)]*)\)/g;

function sourceFilesUnder(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) return sourceFilesUnder(path);
    return /\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) ? [path] : [];
  });
}

function callsInTheFeature(): { file: string; describing: boolean }[] {
  return sourceFilesUnder(ROOT).flatMap((path) => {
    const source = readFileSync(path, 'utf8');

    if (
      source.includes('export function useJobsForConversation') ||
      source.includes('export function fetchJobsForConversation')
    ) {
      return [];
    }

    const relative = path.slice(ROOT.length + 1).replace(/\\/g, '/');

    return [...source.matchAll(CALL)].map((match) => ({
      file: relative,
      describing: match[0].includes(', true'),
    }));
  });
}

describe('which of the route’s two questions each screen asks', () => {
  it('every screen that reads engagements is named here, and no other', () => {
    const found = [...new Set(callsInTheFeature().map((call) => call.file))].sort();

    expect(found.length).toBeGreaterThan(0);

    expect(found).toEqual(Object.keys(CALLERS).sort());
  });

  it('asks the question its entry declares', () => {
    for (const call of callsInTheFeature()) {
      const declared = CALLERS[call.file];
      expect(
        declared,
        `${call.file} reads engagements and is not named in this test`,
      ).toBeDefined();
      if (declared === undefined) continue;
      expect(
        call.describing,
        `${call.file} asks the ${call.describing ? 'describing' : 'offering'} question. ` +
          `Its entry says ${declared.question}: ${declared.why}`,
      ).toBe(declared.question === 'describing');
    }
  });
});
