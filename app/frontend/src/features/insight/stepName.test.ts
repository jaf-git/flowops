// @vitest-environment node
import { describe, expect, it } from 'vitest';

import { readStepKind, stepName } from './stepName';

describe('reading a step kind identifier', () => {
  it('names a step by the activity somebody chose', () => {
    expect(stepName('K:CONTENT@write-the-caption')).toBe('Write the caption');
    expect(stepName('K:STRATEGY@concept-and-references')).toBe('Concept and references');
  });

  it('falls back to the kind of work where nobody named an activity', () => {
    expect(stepName('K:CONTENT')).toBe('Content');
    expect(stepName('K:CLIENT_INTAKE')).toBe('Client intake');
  });

  it('keeps the kind of work available beside the activity, because both are true of the step', () => {
    const read = readStepKind('K:CONTENT@write-the-caption');
    expect(read.workType).toBe('Content');
    expect(read.namesAnActivity).toBe(true);
  });

  it('reads the identifier past an output type, a conversation split and a concept', () => {
    expect(stepName('K:WRITER:TEXT')).toBe('Writer');
    expect(readStepKind('K:PHOTO/dm-tk').subprocess).toBe(true);
    expect(stepName('K:CONTENT@caption#CAPTIONS')).toBe('Caption');
  });

  it('hands back anything it does not recognise rather than showing an empty step', () => {
    expect(stepName('')).toBe('');
    expect(stepName('K:')).toBe('');
  });
});
