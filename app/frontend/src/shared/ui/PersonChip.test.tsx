// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { i18next } from '../../i18n';
import { PersonChip } from './PersonChip';

describe('PersonChip', () => {
  afterEach(() => {
    cleanup();
    void i18next.changeLanguage('en');
  });

  it('names the person', () => {
    render(<PersonChip name="Elena Marinescu" />);

    expect(screen.getByText('Elena Marinescu')).toBeDefined();
  });

  it('derives initials from the name rather than requiring an image', () => {
    render(<PersonChip name="Elena Marinescu" />);

    expect(screen.getByText('EM')).toBeDefined();
  });

  it('renders Former member for an erased person, and never their name', () => {
    render(<PersonChip name="Elena Marinescu" erased />);

    expect(screen.getByText('Former member')).toBeDefined();
    expect(screen.queryByText('Elena Marinescu')).toBeNull();
  });

  it('shows no initials for an erased person, because initials are personal data', () => {
    render(<PersonChip name="Elena Marinescu" erased />);

    expect(screen.queryByText('EM')).toBeNull();
  });

  it('treats a missing name as erasure rather than rendering an empty chip', () => {
    render(<PersonChip name={null} />);

    expect(screen.getByText('Former member')).toBeDefined();
  });

  it('names the role when one is given, so attribution does not rest on colour alone', () => {
    render(<PersonChip name="Ionuț Popescu" role="Manager" />);

    expect(screen.getByText('Manager')).toBeDefined();
  });
});
