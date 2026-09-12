// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { ScrollRegion } from './ScrollRegion';

afterEach(cleanup);

const CONSENT_TEXT =
  'By accepting this invitation you agree to the terms of the Atelier Verde workspace.';

describe('the scroll region', () => {
  it('can be entered with the keyboard, not only scrolled with a mouse', () => {
    render(<ScrollRegion label="Terms of the workspace">{CONSENT_TEXT}</ScrollRegion>);
    const region = screen.getByRole('region', { name: 'Terms of the workspace' });

    region.focus();

    expect(document.activeElement).toBe(region);
  });

  it('announces what it is to someone who lands in it with a screen reader', () => {
    render(<ScrollRegion label="Terms of the workspace">{CONSENT_TEXT}</ScrollRegion>);

    expect(screen.getByRole('region', { name: 'Terms of the workspace' })).toBeTruthy();
  });

  it('keeps the consent text inside the bounded region instead of letting it spill onto the page', () => {
    render(<ScrollRegion label="Terms of the workspace">{CONSENT_TEXT}</ScrollRegion>);
    const region = screen.getByRole('region', { name: 'Terms of the workspace' });
    const text = screen.getByText(CONSENT_TEXT);

    expect(region.contains(text)).toBe(true);
    expect(region.style.overflowY).toBe('auto');
    expect(region.style.maxHeight).not.toBe('');
  });

  it('lets a caller shrink the bound to fit a shorter step', () => {
    render(
      <ScrollRegion label="Terms of the workspace" maxHeight={120}>
        {CONSENT_TEXT}
      </ScrollRegion>,
    );
    const region = screen.getByRole('region', { name: 'Terms of the workspace' });

    expect(region.style.maxHeight).toBe('120px');
  });
});
