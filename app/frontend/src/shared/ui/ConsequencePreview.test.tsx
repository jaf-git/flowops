// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { ConsequencePreview } from './ConsequencePreview';

describe('ConsequencePreview', () => {
  afterEach(cleanup);

  it('lists each consequence separately, so none is buried in a paragraph', () => {
    render(
      <ConsequencePreview
        heading="Deactivating Elena Marinescu will:"
        consequences={[
          'End every session she currently holds.',
          'Move her two direct reports to her manager.',
        ]}
      />,
    );

    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });

  it('gives the list an accessible name, so it is not an anonymous list of sentences', () => {
    render(
      <ConsequencePreview
        heading="Deactivating Elena Marinescu will:"
        consequences={['End every session she currently holds.']}
      />,
    );

    expect(screen.getByRole('list', { name: 'Deactivating Elena Marinescu will:' })).toBeDefined();
  });

  it('shows the heading to the reader, not only to assistive technology', () => {
    render(
      <ConsequencePreview
        heading="Erasing this person will:"
        consequences={['Destroy their name.']}
      />,
    );

    expect(screen.getByText('Erasing this person will:')).toBeDefined();
  });

  it('renders nothing at all when there is nothing to warn about', () => {
    const { container } = render(<ConsequencePreview heading="This will:" consequences={[]} />);

    expect(container.firstChild).toBeNull();
  });
});
