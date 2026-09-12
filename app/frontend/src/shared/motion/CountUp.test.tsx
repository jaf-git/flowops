// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { CountUp } from './CountUp';

afterEach(() => {
  cleanup();
});

describe('a figure that rolls to its value', () => {
  it('rounds when nobody said how to read it', () => {
    render(
      <p>
        <CountUp value={18.5} />
      </p>,
    );

    expect(screen.getByText('19')).toBeTruthy();
  });

  it('hands the formatter the real number rather than a rounded one', () => {
    render(
      <p>
        <CountUp value={18.5} format={(open) => `${String(open)}h`} />
      </p>,
    );

    expect(screen.getByText('18.5h')).toBeTruthy();
  });

  it('starts at the value it is given, so a first paint is never wrong', () => {
    render(
      <p>
        <CountUp value={42} />
      </p>,
    );

    expect(screen.getByText('42')).toBeTruthy();
  });
});
