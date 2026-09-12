// @vitest-environment jsdom

import { act, cleanup, fireEvent, render as renderBare, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { WorkspaceSettings } from '../api/workspaceApi';
import { SettingsScreen } from './SettingsScreen';
import { NoticeCentre } from '../../../shared/notice/NoticeCentre';
import { NoticeProvider } from '../../../shared/notice/NoticeProvider';

function render(ui: Parameters<typeof renderBare>[0]): ReturnType<typeof renderBare> {
  return renderBare(
    <NoticeProvider>
      {ui}
      <NoticeCentre label="notices" dismissLabel="dismiss" />
    </NoticeProvider>,
  );
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key}:${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const settings = {
  data: undefined as WorkspaceSettings | undefined,
  isPending: false,
  isError: false,
};
const save = { mutate: vi.fn(), isPending: false, error: null as Error | null };

vi.mock('../hooks/useWorkspaceSettings', () => ({
  useWorkspaceSettings: () => settings,
  useUpdateWorkspaceSettings: () => save,
}));

function settingsOf(overrides: Partial<WorkspaceSettings> = {}): WorkspaceSettings {
  return {
    name: 'Atelier București',
    use: 'WORK',
    timezone: 'Europe/Bucharest',
    workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    workingHoursStart: '09:00',
    workingHoursEnd: '17:00',
    atRiskWindowHours: 24,
    escalationIntervalsHours: [24, 72, 168],
    quietHoursStart: '22:00',
    quietHoursEnd: '06:00',

    closureCoverageThresholdPercent: 90,
    templateIdleWindowDays: 90,
    invitationApprovalRequired: false,
    effectiveFrom: '2026-08-10T09:00:00Z',
    changedFields: [],
    ...overrides,
  };
}

beforeEach(() => {
  settings.data = settingsOf();
  settings.isPending = false;
  settings.isError = false;
  save.mutate = vi.fn();
  save.isPending = false;
  save.error = null;
});

afterEach(cleanup);

describe('the settings screen', () => {
  it('groups by what each setting affects', () => {
    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.identity')).not.toBeNull();
    expect(screen.queryByText('workspace.settings.time')).not.toBeNull();
    expect(screen.queryByText('workspace.settings.thresholds')).not.toBeNull();
    expect(screen.queryByText('workspace.settings.quietHours')).not.toBeNull();
    expect(screen.queryByText('workspace.settings.invitations')).not.toBeNull();
  });

  it('says what the time settings are used for', () => {
    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.timeExplains')).not.toBeNull();
  });

  it('says that quiet hours may run past midnight, before anybody is refused for trying', () => {
    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.quietHoursExplains')).not.toBeNull();
    expect(screen.getByLabelText('workspace.settings.quietHoursStart')).toHaveProperty(
      'value',
      '22:00',
    );
    expect(screen.getByLabelText('workspace.settings.quietHoursEnd')).toHaveProperty(
      'value',
      '06:00',
    );
  });

  it('says the change applies from now rather than backwards', () => {
    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.appliesFromNow')).not.toBeNull();
  });

  it('submits what is on screen, including an edited value', () => {
    render(<SettingsScreen />);

    fireEvent.change(screen.getByLabelText('workspace.settings.atRiskWindow'), {
      target: { value: '48' },
    });
    fireEvent.click(screen.getByRole('button', { name: /workspace\.settings\.save/ }));

    expect(save.mutate).toHaveBeenCalledTimes(1);
    expect(save.mutate.mock.calls[0]?.[0]).toMatchObject({ atRiskWindowHours: 48 });
  });

  it('reads the escalation ladder back as a list of hours', () => {
    render(<SettingsScreen />);

    fireEvent.change(screen.getByLabelText(/workspace\.settings\.escalation/), {
      target: { value: '12, 36, 96' },
    });
    fireEvent.click(screen.getByRole('button', { name: /workspace\.settings\.save/ }));

    expect(save.mutate.mock.calls[0]?.[0]).toMatchObject({
      escalationIntervalsHours: [12, 36, 96],
    });
  });

  it('turning a working day off removes it from the submission', () => {
    render(<SettingsScreen />);

    fireEvent.click(screen.getByLabelText('workspace.settings.day.MONDAY'));
    fireEvent.click(screen.getByRole('button', { name: /workspace\.settings\.save/ }));

    expect(save.mutate.mock.calls[0]?.[0]).toMatchObject({
      workingDays: ['TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    });
  });

  it('says which rule broke when the server names one', () => {
    save.error = new ApiError(422, { code: 'ESCALATION_INTERVALS_UNORDERED' });

    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.error.ladderUnordered')).not.toBeNull();
    expect(screen.queryByText('workspace.settings.error.couldNotSave')).toBeNull();
  });

  it('names the quiet-hours rule rather than the ladder rule, when that is the one that broke', () => {
    save.error = new ApiError(422, { code: 'QUIET_HOURS_COVER_THE_DAY' });

    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.error.quietHoursCoverTheDay')).not.toBeNull();
    expect(screen.queryByText('workspace.settings.error.ladderUnordered')).toBeNull();
  });

  it('says the save failed when the failure is not a rule the server named', () => {
    save.error = new Error('the connection went away');

    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.error.couldNotSave')).not.toBeNull();
  });

  it('says nothing changed when the server reports no moved fields', () => {
    render(<SettingsScreen />);

    fireEvent.click(screen.getByRole('button', { name: /workspace\.settings\.save/ }));

    act(() => save.mutate.mock.calls[0]?.[1]?.onSuccess?.(settingsOf({ changedFields: [] })));

    expect(screen.queryByText('workspace.settings.nothingChanged')).not.toBeNull();
  });

  it('counts what moved when something did', () => {
    render(<SettingsScreen />);

    fireEvent.click(screen.getByRole('button', { name: /workspace\.settings\.save/ }));
    act(() =>
      save.mutate.mock.calls[0]?.[1]?.onSuccess?.(
        settingsOf({ changedFields: ['atRiskWindowHours', 'quietHoursStart'] }),
      ),
    );

    expect(screen.queryByText(/workspace\.settings\.saved/)).not.toBeNull();
    expect(screen.queryByText('workspace.settings.nothingChanged')).toBeNull();
  });

  it('shows the in-flight state while saving', () => {
    save.isPending = true;

    render(<SettingsScreen />);

    const busy = screen.getByRole('button', { name: 'workspace.settings.saving' });
    expect(busy.getAttribute('aria-busy')).toBe('true');
  });

  it('says so when the settings could not be read at all', () => {
    settings.data = undefined;
    settings.isError = true;

    render(<SettingsScreen />);

    expect(screen.queryByText('workspace.settings.error.heading')).not.toBeNull();
  });
});
