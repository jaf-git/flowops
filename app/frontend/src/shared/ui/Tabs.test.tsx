// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useState, type JSX } from 'react';
import { afterEach, describe, expect, it } from 'vitest';

import { Tabs } from './Tabs';

afterEach(cleanup);

const TABS = [
  { id: 'people', label: 'People' },
  { id: 'invitations', label: 'Invitations' },
  { id: 'activity', label: 'Activity', disabled: true },
  { id: 'settings', label: 'Settings' },
];

function Harness(): JSX.Element {
  const [active, setActive] = useState('people');

  return <Tabs label="Workspace" tabs={TABS} active={active} onSelect={setActive} />;
}

describe('the tabs', () => {
  it('is a single tab stop, with the selected tab holding it', () => {
    render(<Harness />);

    const stops = screen.getAllByRole('tab').filter((tab) => tab.getAttribute('tabindex') !== '-1');

    expect(stops).toHaveLength(1);
    expect(stops[0]?.textContent).toBe('People');
  });

  it('moves with the arrow keys', () => {
    render(<Harness />);

    fireEvent.keyDown(screen.getByRole('tab', { name: 'People' }), { key: 'ArrowRight' });

    expect(screen.getByRole('tab', { name: 'Invitations' }).getAttribute('aria-selected')).toBe(
      'true',
    );
  });

  it('steps over a tab that cannot be selected', () => {
    render(<Harness />);

    fireEvent.keyDown(screen.getByRole('tab', { name: 'People' }), { key: 'ArrowRight' });
    fireEvent.keyDown(screen.getByRole('tab', { name: 'Invitations' }), { key: 'ArrowRight' });

    expect(screen.getByRole('tab', { name: 'Settings' }).getAttribute('aria-selected')).toBe(
      'true',
    );
  });

  it('wraps at the end and answers Home and End', () => {
    render(<Harness />);

    fireEvent.keyDown(screen.getByRole('tab', { name: 'People' }), { key: 'ArrowLeft' });
    expect(screen.getByRole('tab', { name: 'Settings' }).getAttribute('aria-selected')).toBe(
      'true',
    );

    fireEvent.keyDown(screen.getByRole('tab', { name: 'Settings' }), { key: 'Home' });
    expect(screen.getByRole('tab', { name: 'People' }).getAttribute('aria-selected')).toBe('true');
  });

  it('is named', () => {
    render(<Harness />);

    expect(screen.getByRole('tablist').getAttribute('aria-label')).toBe('Workspace');
  });
});
