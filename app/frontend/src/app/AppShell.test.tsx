// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AppShell } from './AppShell';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

vi.mock('../features/notification', () => ({
  NotificationBell: () => <span>bell</span>,
}));

const EVERYTHING = [
  'TASK_VIEW_ANY',
  'PROCESS_VIEW_ANY',
  'PROCESS_INSTANTIATE',
  'CHAT_PARTICIPATE',
  'PEOPLE_VIEW',
  'WORKSPACE_CONFIGURE',
  'PIPELINE_RUN_VIEW',
  'DISCOVERY_CANVAS_VIEW',
];

afterEach(cleanup);

function draw(onNewProcess: (() => void) | undefined): void {
  render(
    <MemoryRouter initialEntries={['/en']}>
      <AppShell
        section="tasks"
        onSection={() => undefined}
        permissions={EVERYTHING}
        personName="maria@atelier.ro"
        onNewProcess={onNewProcess}
      >
        <p>the page</p>
      </AppShell>
    </MemoryRouter>,
  );
}

describe('AppShell, the shell action', () => {
  it('runs the action it was given rather than navigating somewhere', () => {
    const pressed: string[] = [];
    draw(() => pressed.push('asked to start a process'));

    fireEvent.click(screen.getByRole('button', { name: 'shell.newProcess' }));

    // The regression this guards: the button used to navigate to the Processes section,
    // which did nothing at all for somebody already standing there.
    expect(pressed).toEqual(['asked to start a process']);
  });

  it('offers no button at all when the caller may not start a process', () => {
    draw(undefined);

    expect(screen.queryByRole('button', { name: 'shell.newProcess' })).toBeNull();
  });
});
