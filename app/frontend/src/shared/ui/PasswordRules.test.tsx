// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { PasswordRules } from './PasswordRules';
import { passwordRulesMet } from './passwordPolicy';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

afterEach(cleanup);

describe('PasswordRules', () => {
  it('shows every rule as unmet before anything is typed', () => {
    render(<PasswordRules password="" id="rules" />);

    expect(screen.getAllByRole('listitem').every((item) => item.dataset.met === 'no')).toBe(true);
  });

  it('marks the length rule met the moment it is met, and not before', () => {
    expect(passwordRulesMet('a'.repeat(11)).MINIMUM_LENGTH).toBe(false);
    expect(passwordRulesMet('a'.repeat(12)).MINIMUM_LENGTH).toBe(true);
  });

  it('marks the ceiling unmet only once it is exceeded', () => {
    expect(passwordRulesMet('a'.repeat(128)).MAXIMUM_LENGTH).toBe(true);
    expect(passwordRulesMet('a'.repeat(129)).MAXIMUM_LENGTH).toBe(false);
  });

  it('renders a live region, because the list changes without anything being clicked', () => {
    render(<PasswordRules password="a-long-enough-passphrase" id="rules" />);

    expect(screen.getByRole('list').getAttribute('aria-live')).toBe('polite');
  });

  it('states met and unmet in text, not only in colour', () => {
    render(<PasswordRules password="a-long-enough-passphrase" id="rules" />);

    expect(screen.getAllByText(/ui.passwordRules.met/).length).toBeGreaterThan(0);
  });
});
