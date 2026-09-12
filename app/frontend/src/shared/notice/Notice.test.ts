import { describe, expect, it } from 'vitest';

import { afterAnnouncing, dwellFor, NOTICE_DWELL_MS, type Notice } from './Notice';

function notice(id: string): Notice {
  return { id, tone: 'done', message: id };
}

describe('the stack keeps the newest and drops the rest', () => {
  it('adds to the end', () => {
    expect(afterAnnouncing([notice('a')], notice('b')).map((one) => one.id)).toEqual(['a', 'b']);
  });

  it('never grows past what is shown, so a notice cannot outlive the moment it is about', () => {
    const standing = [notice('a'), notice('b'), notice('c')];

    expect(afterAnnouncing(standing, notice('d')).map((one) => one.id)).toEqual(['b', 'c', 'd']);
  });

  it('drops the oldest rather than refusing the newest', () => {
    const full = [notice('a'), notice('b'), notice('c')];

    expect(afterAnnouncing(full, notice('d')).some((one) => one.id === 'd')).toBe(true);
  });
});

describe('a failure waits to be read', () => {
  it('gives something that worked a dwell and then goes', () => {
    expect(dwellFor({ tone: 'done' })).toBe(NOTICE_DWELL_MS);
  });

  it('never dismisses a failure on its own — an error nobody saw is an error that did not happen', () => {
    expect(dwellFor({ tone: 'failed' })).toBeUndefined();
  });

  it('lets an offer stand longer, because it is asking for a decision rather than reporting one', () => {
    expect(dwellFor({ tone: 'offer', dwellMs: 30_000 })).toBe(30_000);
  });

  it('will not let a stated dwell keep a failure on screen and then remove it', () => {
    expect(dwellFor({ tone: 'failed', dwellMs: 1_000 })).toBeUndefined();
  });
});
