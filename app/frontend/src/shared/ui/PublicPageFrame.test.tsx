// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { PublicPageFrame } from './PublicPageFrame';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

afterEach(cleanup);

function renderAt(path: string, heading?: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PublicPageFrame heading={heading}>
        <p>the page</p>
      </PublicPageFrame>
    </MemoryRouter>,
  );
}

describe('the public page frame', () => {
  it('names the product and renders what it was given', () => {
    renderAt('/en/invitation');

    expect(screen.getByText('app.name')).toBeDefined();
    expect(screen.getByText('the page')).toBeDefined();
  });

  it('shows a heading only when one is given', () => {
    renderAt('/en/reset-password');
    expect(screen.queryByRole('heading', { level: 2 })).toBeNull();

    cleanup();
    renderAt('/en/reset-password', 'Choose a new password');
    expect(screen.getByRole('heading', { level: 2 }).textContent).toBe('Choose a new password');
  });
});
