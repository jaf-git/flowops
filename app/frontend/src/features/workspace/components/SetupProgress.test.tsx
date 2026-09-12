// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { SetupDraft } from '../model/setupDraft';
import { SetupProgress } from './SetupProgress';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, unknown>) =>
      values === undefined ? key : `${key} ${JSON.stringify(values)}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function draft(overrides: Partial<SetupDraft> = {}): SetupDraft {
  return {
    ownerName: '',
    workspaceName: '',
    use: 'WORK',
    timezone: 'Europe/Bucharest',
    ...overrides,
  };
}

describe('SetupProgress', () => {
  afterEach(cleanup);

  it('counts what is answered, and says so where it will be announced', () => {
    render(<SetupProgress draft={draft()} />);

    const counted = screen.getByText(/workspace.setup.panel.counted/);
    expect(counted.textContent).toContain('"done":2');
    expect(counted.textContent).toContain('"total":4');
    expect(counted.getAttribute('aria-live')).toBe('polite');
  });

  it('stops counting and says it is ready once all four are answered', () => {
    render(
      <SetupProgress draft={draft({ ownerName: 'Maria Ionescu', workspaceName: 'Atelier' })} />,
    );

    expect(screen.getByText('workspace.setup.panel.ready')).toBeDefined();
    expect(screen.queryByText(/workspace.setup.panel.counted/)).toBeNull();
  });

  it('shows what the owner answered, not only that they answered', () => {
    render(<SetupProgress draft={draft({ ownerName: 'Ionuț Ștefănescu' })} />);

    expect(screen.getByText('Ionuț Ștefănescu')).toBeDefined();
    expect(screen.getByText('Europe/Bucharest')).toBeDefined();
  });

  it('marks a satisfied step and leaves an unsatisfied one unmarked', () => {
    const { container } = render(<SetupProgress draft={draft({ ownerName: 'Maria Ionescu' })} />);

    const marks = container.querySelectorAll('.fo-mark');
    expect(marks).toHaveLength(4);

    expect(container.querySelectorAll('.fo-mark-done')).toHaveLength(3);
    expect(marks[1]?.className).not.toContain('fo-mark-done');
    expect(marks[1]?.querySelector('svg')).toBeNull();
    expect(marks[0]?.querySelector('svg')).not.toBeNull();
  });

  it('names itself, so the panel is reachable as a landmark rather than being unlabelled scenery', () => {
    render(<SetupProgress draft={draft()} />);

    expect(
      screen.getByRole('complementary', { name: 'workspace.setup.panel.label' }),
    ).toBeDefined();
  });
});
