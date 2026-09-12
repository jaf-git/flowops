// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { SetupPrefill } from '../api/workspaceApi';
import { WorkspaceSetupScreen } from './WorkspaceSetupScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let prefillState: { isPending: boolean; isError: boolean; data: SetupPrefill | undefined } = {
  isPending: false,
  isError: false,
  data: undefined,
};

vi.mock('../hooks/useWorkspaceSetup', () => ({
  useSetupPrefill: () => prefillState,
  useSetUpWorkspace: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null }),
}));
vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

function prefill(overrides: Partial<SetupPrefill> = {}): SetupPrefill {
  return {
    setupCompleted: false,
    suggestedTimezone: 'Europe/Bucharest',
    availableTimezones: ['Europe/Bucharest', 'Europe/London'],
    ...overrides,
  };
}

describe('the setup screen', () => {
  afterEach(() => {
    cleanup();
    prefillState = { isPending: false, isError: false, data: undefined };
  });

  it('renders the wizard and the account beside it', () => {
    prefillState = { isPending: false, isError: false, data: prefill() };

    render(<WorkspaceSetupScreen accountStrip={<div>account-strip</div>} />);

    expect(screen.getByLabelText('workspace.setup.ownerName.label')).toBeDefined();
    expect(screen.getByText('account-strip')).toBeDefined();
  });

  it('keeps the account reachable while loading and when the screen cannot load', () => {
    prefillState = { isPending: true, isError: false, data: undefined };
    const loading = render(<WorkspaceSetupScreen accountStrip={<div>account-strip</div>} />);
    expect(loading.getByText('account-strip')).toBeDefined();
    cleanup();

    prefillState = { isPending: false, isError: true, data: undefined };
    render(<WorkspaceSetupScreen accountStrip={<div>account-strip</div>} />);
    expect(screen.getByText('account-strip')).toBeDefined();
  });

  it('does not render the wizard once setup is complete', () => {
    prefillState = { isPending: false, isError: false, data: prefill({ setupCompleted: true }) };

    render(<WorkspaceSetupScreen accountStrip={<div>account-strip</div>} />);

    expect(screen.queryByLabelText('workspace.setup.ownerName.label')).toBeNull();
    expect(screen.getByText('workspace.setup.alreadyDone')).toBeDefined();
  });
});
