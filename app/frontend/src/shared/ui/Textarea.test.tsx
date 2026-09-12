// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { Field } from './Field';
import { Textarea } from './Textarea';

afterEach(cleanup);

describe('the multi-line text control', () => {
  it('is reached by its label rather than by a placeholder', () => {
    render(
      <Field id="reason" label="What is the work waiting on?">
        <Textarea id="reason" />
      </Field>,
    );

    expect(screen.getByLabelText('What is the work waiting on?')).toBeInstanceOf(
      HTMLTextAreaElement,
    );
  });

  it('announces that it is invalid rather than only looking it', () => {
    render(<Textarea id="note" invalid />);

    expect(screen.getByRole('textbox').getAttribute('aria-invalid')).toBe('true');
  });

  it('carries no invalid state when it is fine, so nothing is announced', () => {
    render(<Textarea id="note" />);

    expect(screen.getByRole('textbox').getAttribute('aria-invalid')).toBeNull();
  });

  it('points at the error the field rendered, so the reason is read out with the control', () => {
    render(
      <Field id="note" label="What did you do?" error="Say what you did.">
        <Textarea id="note" invalid describedBy="error" />
      </Field>,
    );

    expect(screen.getByLabelText('What did you do?').getAttribute('aria-describedby')).toBe(
      'note-error',
    );
  });

  it('opens with room for more than one line, because a few words is not a reason', () => {
    render(<Textarea id="reason" />);

    expect(screen.getByRole('textbox').getAttribute('rows')).toBe('3');
  });
});
