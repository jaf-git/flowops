// @vitest-environment jsdom

import { act, cleanup, fireEvent, render as renderBare, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { OwnData } from '../api/workspaceApi';
import { MyDataScreen } from './MyDataScreen';
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

const own = { data: undefined as OwnData | undefined, isPending: false, isError: false };
const exportCopy = { mutate: vi.fn(), isPending: false, error: null as Error | null };
const editProfile = { mutate: vi.fn(), isPending: false, error: null as Error | null };

vi.mock('../hooks/useOwnData', () => ({
  useOwnData: () => own,
  useExportOwnData: () => exportCopy,
  useEditOwnProfile: () => editProfile,
}));

function fileOf(overrides: Partial<OwnData> = {}): OwnData {
  return {
    account: {
      personId: 'person-ioana',
      emailAddress: 'ioana@atelier.ro',
      displayName: 'Ioana Radu',
      role: 'EMPLOYEE',
      accountState: 'ACTIVE',
      createdAt: '2026-01-04T09:00:00Z',
      sessions: [
        {
          reference: 'session-1',
          deviceSummary: 'Firefox on Windows',
          coarseLocation: 'București',
          createdAt: '2026-08-10T08:00:00Z',
        },
      ],
    },
    membership: {
      membershipId: 'membership-ioana',
      status: 'ACTIVE',
      deactivatedAt: null,
      managerName: 'Ionuț Petrescu',
    },
    reportingLineHistory: [
      { managerName: 'Maria Ionescu', from: '2026-01-04T09:00:00Z', until: '2026-05-01T09:00:00Z' },
      { managerName: 'Ionuț Petrescu', from: '2026-05-01T09:00:00Z', until: null },
    ],
    consent: { version: 'v1', language: 'ro', agreedAt: '2026-01-04T09:00:00Z' },
    authored: { tasks: 0, comments: 0, approvals: 0 },
    exports: { produced: 1, limit: 5 },
    producedForSomebodyElse: false,
    ...overrides,
  };
}

beforeEach(() => {
  own.data = fileOf();
  own.isPending = false;
  own.isError = false;
  exportCopy.mutate = vi.fn();
  exportCopy.isPending = false;
  exportCopy.error = null;
  editProfile.mutate = vi.fn();
  editProfile.isPending = false;
  editProfile.error = null;
});

afterEach(cleanup);

function nameField(): HTMLElement {
  return screen.getByLabelText(/workspace\.myProfile\.nameLabel/);
}

describe('the my-data screen', () => {
  it('shows every section the criterion lists', () => {
    render(<MyDataScreen />);

    expect(screen.queryByText('workspace.myData.account')).not.toBeNull();
    expect(screen.queryByText('workspace.myData.reportingLine')).not.toBeNull();
    expect(screen.queryByText('workspace.myData.consent')).not.toBeNull();
    expect(screen.queryByText('workspace.myData.sessions')).not.toBeNull();
    expect(screen.queryByText('workspace.myData.authored')).not.toBeNull();
  });

  it('shows both stretches of the reporting line, with the current one marked as still now', () => {
    render(<MyDataScreen />);

    expect(screen.queryByText(/Maria Ionescu/)).not.toBeNull();
    expect(screen.queryByText(/workspace\.myData\.stillNow/)).not.toBeNull();
  });

  it('names the consent version the person actually read', () => {
    render(<MyDataScreen />);

    expect(screen.queryByText(/"version":"v1"/)).not.toBeNull();
  });

  it('shows the authored section even when everything in it is zero', () => {
    render(<MyDataScreen />);

    expect(screen.queryByText(/workspace\.myData\.authoredCounts/)).not.toBeNull();
  });

  it('offers the copy while any remain', () => {
    render(<MyDataScreen />);

    expect(screen.getByRole('button', { name: /workspace\.myData\.takeACopy/ })).toHaveProperty(
      'disabled',
      false,
    );
  });

  it('refuses the copy once today is spent, rather than failing on the press', () => {
    own.data = fileOf({ exports: { produced: 5, limit: 5 } });

    render(<MyDataScreen />);

    expect(screen.getByRole('button', { name: /workspace\.myData\.takeACopy/ })).toHaveProperty(
      'disabled',
      true,
    );
  });

  it('hands the person an actual file when the export succeeds', () => {
    const clicked: string[] = [];
    const createObjectURL = vi.fn(() => 'blob:my-data');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const realClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function click(this: HTMLAnchorElement) {
      clicked.push(this.download);
    };

    try {
      render(<MyDataScreen />);
      fireEvent.click(screen.getByRole('button', { name: /workspace\.myData\.takeACopy/ }));
      act(() => exportCopy.mutate.mock.calls[0]?.[1]?.onSuccess?.(fileOf()));

      expect(createObjectURL).toHaveBeenCalledTimes(1);
      expect(clicked).toEqual(['my-data.json']);
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:my-data');
    } finally {
      HTMLAnchorElement.prototype.click = realClick;
    }
  });

  it('says it is a limit on how often rather than a refusal, when the server says so', () => {
    exportCopy.error = new ApiError(429, { code: 'EXPORT_LIMIT' });

    render(<MyDataScreen />);

    expect(screen.queryByText('workspace.myData.exportLimit')).not.toBeNull();
  });

  it('says something when the export fails in a way the server did not describe', () => {
    exportCopy.error = new Error('the connection went away');

    render(<MyDataScreen />);

    expect(screen.queryByText('workspace.myData.exportFailed')).not.toBeNull();
  });

  it('shows the name currently held, and saves the one typed over it', () => {
    render(<MyDataScreen />);

    expect(nameField()).toHaveProperty('value', 'Ioana Radu');
    fireEvent.change(nameField(), { target: { value: 'Ioana Radu-Marin' } });
    fireEvent.click(screen.getByRole('button', { name: /workspace\.myProfile\.save/ }));

    expect(editProfile.mutate).toHaveBeenCalledTimes(1);
    expect(editProfile.mutate.mock.calls[0]?.[0]).toBe('Ioana Radu-Marin');
  });

  it('refuses to save a name the person has emptied', () => {
    render(<MyDataScreen />);

    fireEvent.change(nameField(), { target: { value: '   ' } });

    expect(nameField()).toHaveProperty('value', '   ');
    expect(screen.getByRole('button', { name: /workspace\.myProfile\.save/ })).toHaveProperty(
      'disabled',
      true,
    );
  });

  it('says the address cannot be changed here, and why', () => {
    render(<MyDataScreen />);

    expect(screen.queryByText(/workspace\.myProfile\.addressIsFixed/)).not.toBeNull();
  });

  it('says so when the owner produced this on somebody else behalf', () => {
    own.data = fileOf({ producedForSomebodyElse: true });

    render(<MyDataScreen />);

    expect(screen.queryByText('workspace.myData.producedForSomebodyElse')).not.toBeNull();
  });

  it('does not say so on a person own file', () => {
    render(<MyDataScreen />);

    expect(screen.queryByText('workspace.myData.producedForSomebodyElse')).toBeNull();
  });
});
