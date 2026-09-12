// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { LandingTarget, SessionContext } from '../features/auth';
import { App } from './App';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let currentSession: SessionContext | null = null;

vi.mock('../features/auth/hooks/useAuth', () => ({
  useSessionContext: () => ({ isPending: false, data: currentSession }),
  useLogout: () => ({ mutate: vi.fn(), isPending: false }),
  useLogin: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null }),
  useOwnSessions: () => ({ isPending: false, isError: false, data: [] }),
  useUserSessions: () => ({ isPending: false, isError: false, data: undefined, error: null }),
  useReauthenticate: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null }),
  useTerminateSession: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null }),
  useChangePassword: () => ({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}));

vi.mock('../features/workspace/hooks/useWorkspaceSetup', () => ({
  useSetupPrefill: () => ({
    isPending: false,
    isError: false,
    data: {
      setupCompleted: false,
      suggestedTimezone: 'Europe/Bucharest',
      availableTimezones: ['Europe/Bucharest', 'Europe/London'],
    },
  }),
  useSetUpWorkspace: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null }),
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn(), setQueryData: vi.fn() }),

  useQuery: () => ({ isPending: false, isError: false, data: undefined }),

  useQueries: () => [],
  useMutation: () => ({ mutate: vi.fn(), isPending: false, error: null, reset: vi.fn() }),
}));

vi.mock('../features/workspace/hooks/useOwnData', () => ({
  useOwnData: () => ({ isPending: false, isError: true, data: undefined }),
  useExportOwnData: () => ({ mutate: vi.fn(), isPending: false, error: null }),
  useEditOwnProfile: () => ({ mutate: vi.fn(), isPending: false, error: null }),
}));

vi.mock('../features/task/hooks/useTasks', () => ({
  useTasks: () => ({ isPending: false, isError: false, data: { tasks: [] } }),

  useAssignablePeople: () => ({ isPending: false, isError: false, data: { people: [] } }),

  useTaskSections: () => ({
    isPending: false,
    isError: false,
    data: { sections: [{ section: 'needs-you', count: 0 }], total: 0 },
  }),
  useTaskSection: () => ({
    isPending: false,
    isError: false,
    data: { section: 'needs-you', rows: [], page: 0, size: 10, total: 0, totalPages: 1 },
  }),

  useDeadlineNotices: () => ({ data: { notices: [] } }),
  useAcknowledgeDeadlineNotice: () => ({ mutate: vi.fn(), isPending: false }),
  useSetDeadline: () => ({ mutate: vi.fn(), reset: vi.fn(), isPending: false, error: undefined }),
  useAcceptTask: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    variables: undefined,
  }),
  useCreateTask: () => ({ mutate: vi.fn(), reset: vi.fn(), isPending: false, error: undefined }),

  useStartTask: () => ({ mutate: vi.fn(), isPending: false, isError: false, variables: undefined }),
  useUnblockTask: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    variables: undefined,
  }),

  useCloseTask: () => ({ mutate: vi.fn(), isPending: false, isError: false, variables: undefined }),
  useReviewQueue: () => ({ isPending: false, isError: false, data: { tasks: [] } }),
}));

const SIGNED_IN: Record<LandingTarget, { as: string }> = {
  TRIAGE: { as: 'ioana@atelierdelemn.ro' },
  MY_WORK: { as: 'ionut@atelierdelemn.ro' },
  WORKSPACE_SETUP: { as: 'maria@atelierdelemn.ro' },
};

function signedIn(landingTarget: LandingTarget, email: string): SessionContext {
  return {
    userId: `user-${landingTarget}`,
    email,
    accountState: 'ACTIVE',
    permissions: ['SESSION_VIEW_OWN'],
    landingTarget,
    serverTime: '2026-08-20T09:00:00Z',
  };
}

describe('the account, from wherever a person lands', () => {
  afterEach(() => {
    cleanup();
    currentSession = null;
  });

  for (const [landingTarget, { as }] of Object.entries(SIGNED_IN)) {
    it(`is reachable when landing on ${landingTarget}`, async () => {
      currentSession = signedIn(landingTarget as LandingTarget, as);

      render(
        <MemoryRouter>
          <App locale="en" />
        </MemoryRouter>,
      );

      const [toAccount] = screen.queryAllByRole('button', { name: 'shell.nav.account' });
      if (toAccount !== undefined) {
        await userEvent.click(toAccount);
      }

      expect(screen.getAllByRole('button', { name: 'auth.logout.submit' }).length).toBeGreaterThan(
        0,
      );
    });
  }

  it('is absent at the gate, where there is nobody to sign out', () => {
    currentSession = null;

    render(
      <MemoryRouter>
        <App locale="en" />
      </MemoryRouter>,
    );

    expect(screen.queryByRole('button', { name: 'auth.logout.submit' })).toBeNull();
    expect(screen.getByLabelText('auth.field.email')).toBeDefined();
  });
});
