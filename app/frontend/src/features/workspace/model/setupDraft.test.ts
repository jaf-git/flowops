import { describe, expect, it } from 'vitest';

import { isReady, satisfiedCount, stepsOf, type SetupDraft } from './setupDraft';

function draft(overrides: Partial<SetupDraft> = {}): SetupDraft {
  return {
    ownerName: '',
    workspaceName: '',
    use: 'WORK',
    timezone: 'Europe/Bucharest',
    ...overrides,
  };
}

describe('the setup checklist', () => {
  it('counts the two the product answered for the owner', () => {
    expect(satisfiedCount(draft())).toBe(2);
    expect(isReady(draft())).toBe(false);
  });

  it('ticks a step the moment its field is answered', () => {
    expect(satisfiedCount(draft({ ownerName: 'Maria Ionescu' }))).toBe(3);
  });

  it('is ready only when all four are answered', () => {
    expect(isReady(draft({ ownerName: 'Maria Ionescu', workspaceName: 'Atelier Ionescu' }))).toBe(
      true,
    );
  });

  it('does not count a field holding nothing but spaces', () => {
    expect(satisfiedCount(draft({ ownerName: '   ' }))).toBe(2);
  });

  it('keeps the form order however much is answered', () => {
    const order = ['ownerName', 'workspaceName', 'use', 'timezone'];

    expect(stepsOf(draft()).map((step) => step.field)).toEqual(order);
    expect(
      stepsOf(draft({ ownerName: 'Maria', workspaceName: 'Atelier' })).map((s) => s.field),
    ).toEqual(order);
  });

  it('carries the answer once a step is satisfied', () => {
    const steps = stepsOf(draft({ ownerName: '  Maria Ionescu  ' }));

    expect(steps[0]?.answer).toBe('Maria Ionescu');
    expect(steps[3]?.answer).toBe('Europe/Bucharest');
  });
});
