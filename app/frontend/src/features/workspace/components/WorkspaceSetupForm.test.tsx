// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { SetupPrefill } from '../api/workspaceApi';
import type { SetupDraft } from '../model/setupDraft';
import { WorkspaceSetupForm } from './WorkspaceSetupForm';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const mutate = vi.fn();
const changed = vi.fn();
let mutationState: { isPending: boolean; isError: boolean; error: Error | null } = {
  isPending: false,
  isError: false,
  error: null,
};

vi.mock('../hooks/useWorkspaceSetup', () => ({
  useSetUpWorkspace: () => ({ mutate, ...mutationState }),
}));

function handleFor(draft: Partial<SetupDraft> = {}) {
  const value: SetupDraft = {
    ownerName: '',
    workspaceName: '',
    use: 'WORK',
    timezone: 'Europe/Bucharest',
    ...draft,
  };
  return { draft: value, change: changed };
}

function prefill(overrides: Partial<SetupPrefill> = {}): SetupPrefill {
  return {
    setupCompleted: false,
    suggestedTimezone: 'UTC',
    availableTimezones: ['Europe/Bucharest', 'Europe/London', 'UTC'],
    ...overrides,
  };
}

describe('WorkspaceSetupForm', () => {
  afterEach(() => {
    cleanup();
    mutate.mockReset();
    changed.mockReset();
    mutationState = { isPending: false, isError: false, error: null };
    vi.restoreAllMocks();
  });

  it('opens the timezone on a real value rather than on nothing', () => {
    render(<WorkspaceSetupForm prefill={prefill()} handle={handleFor()} />);

    expect(screen.getByLabelText('workspace.setup.timezone.label')).toHaveProperty(
      'value',
      'Europe/Bucharest',
    );
  });

  it('says so when the browser could not report a zone, rather than showing a bare list', () => {
    vi.spyOn(Intl, 'DateTimeFormat').mockReturnValue({
      resolvedOptions: () =>
        ({ timeZone: 'Europe/Atlantis' }) as Intl.ResolvedDateTimeFormatOptions,
    } as Intl.DateTimeFormat);

    render(<WorkspaceSetupForm prefill={prefill()} handle={handleFor()} />);

    expect(screen.getByText('workspace.setup.timezone.notDetected')).toBeDefined();
  });

  it('asks for four things and nothing else', () => {
    render(<WorkspaceSetupForm prefill={prefill()} handle={handleFor()} />);

    expect(screen.getByLabelText('workspace.setup.ownerName.label')).toBeDefined();
    expect(screen.getByLabelText('workspace.setup.workspaceName.label')).toBeDefined();
    expect(screen.getByRole('group', { name: 'workspace.setup.use.label' })).toBeDefined();
    expect(screen.getByLabelText('workspace.setup.timezone.label')).toBeDefined();

    expect(screen.queryByLabelText(/working/i)).toBeNull();
  });

  it('reports every keystroke to the draft it renders from', () => {
    render(<WorkspaceSetupForm prefill={prefill()} handle={handleFor()} />);

    fireEvent.change(screen.getByLabelText('workspace.setup.ownerName.label'), {
      target: { value: 'Maria Ionescu' },
    });
    fireEvent.change(screen.getByLabelText('workspace.setup.workspaceName.label'), {
      target: { value: 'Atelier Ionescu' },
    });
    fireEvent.click(screen.getByLabelText('workspace.setup.use.PERSONAL'));

    expect(changed).toHaveBeenCalledWith('ownerName', 'Maria Ionescu');
    expect(changed).toHaveBeenCalledWith('workspaceName', 'Atelier Ionescu');
    expect(changed).toHaveBeenCalledWith('use', 'PERSONAL');
  });

  it('submits the draft as it stands', () => {
    render(
      <WorkspaceSetupForm
        prefill={prefill()}
        handle={handleFor({ ownerName: 'Maria Ionescu', workspaceName: 'Atelier Ionescu' })}
      />,
    );

    fireEvent.click(screen.getByRole('button'));

    expect(mutate).toHaveBeenCalledWith({
      ownerName: 'Maria Ionescu',
      workspaceName: 'Atelier Ionescu',
      use: 'WORK',
      timezone: 'Europe/Bucharest',
    });
  });

  it('marks the field the server refused', () => {
    mutationState = {
      isPending: false,
      isError: true,
      error: new ApiError(400, {
        code: 'REQUEST_INVALID',
        message: 'The request is not valid.',
        details: [{ field: 'workspaceName', rule: 'NotBlank' }],
      }),
    };

    render(<WorkspaceSetupForm prefill={prefill()} handle={handleFor()} />);

    expect(screen.getByText('workspace.setup.workspaceName.required')).toBeDefined();
    expect(screen.getByLabelText('workspace.setup.workspaceName.label')).toHaveProperty(
      'ariaInvalid',
      'true',
    );

    expect(screen.queryByText('workspace.setup.ownerName.required')).toBeNull();
  });

  it('disables the button while the request is in flight', () => {
    mutationState = { isPending: true, isError: false, error: null };

    render(<WorkspaceSetupForm prefill={prefill()} handle={handleFor()} />);

    expect(screen.getByRole('button')).toHaveProperty('disabled', true);
  });
});
