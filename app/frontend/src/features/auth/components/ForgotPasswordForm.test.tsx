// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { ForgotPasswordForm } from './ForgotPasswordForm';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const requestReset = {
  mutate: vi.fn(),
  isPending: false,
  isSuccess: false,
  isError: false,
};

vi.mock('../hooks/useAuth', () => ({
  useRequestPasswordReset: () => requestReset,
}));

beforeEach(() => {
  requestReset.mutate = vi.fn();
  requestReset.isPending = false;
  requestReset.isSuccess = false;
  requestReset.isError = false;
});

afterEach(cleanup);

describe('the forgotten password form', () => {
  it('sends the address that was typed', () => {
    render(<ForgotPasswordForm onGiveUp={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/auth.field.email/), {
      target: { value: 'maria@atelier.ro' },
    });
    fireEvent.submit(screen.getByText('auth.forgotPassword.submit').closest('form')!);

    expect(requestReset.mutate).toHaveBeenCalledWith('maria@atelier.ro');
  });

  it('shows one conditional notice on success, never a claim that mail was sent', () => {
    requestReset.isSuccess = true;

    render(<ForgotPasswordForm onGiveUp={vi.fn()} />);

    expect(screen.getByText('auth.forgotPassword.sent')).toBeDefined();
    expect(screen.queryByLabelText(/auth.field.email/)).toBeNull();
  });

  it('disables the control while the request is in flight', () => {
    requestReset.isPending = true;

    render(<ForgotPasswordForm onGiveUp={vi.fn()} />);

    const submit = screen.getByText('auth.forgotPassword.sending');
    expect(submit.getAttribute('disabled')).not.toBeNull();
  });

  it('offers a way back to signing in, and calls it', () => {
    const giveUp = vi.fn();
    render(<ForgotPasswordForm onGiveUp={giveUp} />);

    fireEvent.click(screen.getByText('auth.forgotPassword.backToLogin'));

    expect(giveUp).toHaveBeenCalled();
  });
});

describe('the copy', () => {
  it('keeps the notice conditional', () => {
    expect(en.auth.forgotPassword.sent).toContain('{{email}}');
    expect(en.auth.forgotPassword.sent.toLowerCase()).toContain('if ');
  });
});
