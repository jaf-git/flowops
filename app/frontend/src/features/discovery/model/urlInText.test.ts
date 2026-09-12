import { describe, expect, it } from 'vitest';

import { likelyOutput, urlsIn } from './urlInText';

describe('the URL in a marked message', () => {
  it('finds a bare link', () => {
    expect(urlsIn('here they are https://drive.example/summer')).toEqual([
      'https://drive.example/summer',
    ]);
  });

  it('drops the punctuation a sentence puts after a link', () => {
    expect(likelyOutput('shots are up at https://drive.example/shots.')).toBe(
      'https://drive.example/shots',
    );
    expect(likelyOutput('see (https://drive.example/a), and the rest tomorrow')).toBe(
      'https://drive.example/a',
    );
  });

  it('keeps them in the order they were written, first one first', () => {
    const found = urlsIn(
      'final https://drive.example/final and the brief https://docs.example/brief',
    );

    expect(found).toEqual(['https://drive.example/final', 'https://docs.example/brief']);
    expect(likelyOutput('final https://drive.example/final and https://docs.example/brief')).toBe(
      'https://drive.example/final',
    );
  });

  it('counts a link pasted twice once — somebody who repeated it meant it once', () => {
    expect(urlsIn('https://a.example/x see https://a.example/x')).toHaveLength(1);
  });

  it.each([['no link here at all'], [''], [null], [undefined]])('finds nothing in %s', (text) => {
    expect(urlsIn(text)).toEqual([]);
    expect(likelyOutput(text)).toBeNull();
  });

  it('does not treat a bare domain in prose as a link', () => {
    expect(urlsIn('ask atelier.ro about the budget')).toEqual([]);
  });
});
