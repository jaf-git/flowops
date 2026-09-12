import { describe, expect, it } from 'vitest';

import { pipelineReached, somethingHappenedAt } from './stageActivity';

describe('whether anything happened at a stage', () => {
  it('says so when the analysers worked there and the pipeline never reached it', () => {
    expect(somethingHappenedAt('RECOMMEND', 'CORRELATE', 5)).toBe(true);
  });

  it('says so when the pipeline reached it and found nothing to report', () => {
    expect(somethingHappenedAt('NORMALISE', 'CORRELATE', 0)).toBe(true);
  });

  it('says nothing happened only when neither subsystem did anything', () => {
    expect(somethingHappenedAt('RECOMMEND', 'CORRELATE', 0)).toBe(false);
    expect(somethingHappenedAt('OBSERVE', undefined, 0)).toBe(false);
  });

  it('never lets a numeral appear beside a stage the card calls idle', () => {
    const stages = ['OBSERVE', 'NORMALISE', 'MEASURE', 'DETECT', 'CORRELATE', 'RECOMMEND'] as const;
    for (const stage of stages) {
      for (const reached of [undefined, 'OBSERVE', 'DETECT', 'CORRELATE', 'RECOMMEND']) {
        for (const findings of [0, 1, 7]) {
          if (findings > 0) {
            expect(somethingHappenedAt(stage, reached, findings)).toBe(true);
          }
        }
      }
    }
  });

  it('reads the pipeline in dependency order, so reaching one stage means reaching those before it', () => {
    expect(pipelineReached('OBSERVE', 'CORRELATE')).toBe(true);
    expect(pipelineReached('DETECT', 'CORRELATE')).toBe(true);
    expect(pipelineReached('RECOMMEND', 'CORRELATE')).toBe(false);
  });

  it('treats a stage it does not recognise as not reached rather than as the end', () => {
    expect(pipelineReached('OBSERVE', 'ANALYSE')).toBe(false);
    expect(pipelineReached('OBSERVE', undefined)).toBe(false);
  });
});
