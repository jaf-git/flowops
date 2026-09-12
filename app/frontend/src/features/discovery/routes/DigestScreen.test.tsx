// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { ApiError } from '../../../shared/api/client';
import type { DigestDecision, WeeklyDigest } from '../api/digestApi';
import { DigestScreen } from './DigestScreen';

const fetchDigest = vi.fn<() => Promise<WeeklyDigest>>();
const nameType = vi.fn<(typeId: string, name: string) => Promise<void>>();

vi.mock('../api/digestApi', () => ({
  fetchDigest: () => fetchDigest(),
  nameType: (typeId: string, name: string) => nameType(typeId, name),
}));

vi.mock('../api/discoveryApi', () => ({
  answerNudge: vi.fn(),
  blockNode: vi.fn(),
  deleteNode: vi.fn(),
  endThread: vi.fn(),
  fetchJobsForConversation: vi.fn(),
  fetchNudge: vi.fn(),
  markMessage: vi.fn(),
  openJob: vi.fn(),
  recordOutput: vi.fn(),
  relinkNode: vi.fn(),
  resumeNode: vi.fn(),
}));

function lookup(key: string, vars?: Record<string, unknown>): string {
  const at = (path: string): unknown =>
    path
      .split('.')
      .reduce<unknown>(
        (node, step) =>
          typeof node === 'object' && node !== null
            ? (node as Record<string, unknown>)[step]
            : undefined,
        en,
      );

  const count = vars?.count;
  const plural =
    typeof count === 'number' ? at(`${key}_${count === 1 ? 'one' : 'other'}`) : undefined;
  const found = plural ?? at(key);
  const text = typeof found === 'string' ? found : ((vars?.defaultValue as string) ?? key);

  return Object.entries(vars ?? {}).reduce(
    (sentence, [name, value]) => sentence.replaceAll(`{{${name}}}`, String(value)),
    text,
  );
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: lookup, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function proposal(over: Partial<DigestDecision> = {}): DigestDecision {
  return {
    kind: 'CONFIRM_A_NAME',
    typeId: 'type-1',
    subject: 'Account manager → Content writer',
    occurrenceCount: 5,
    ...over,
  };
}

function week(over: Partial<WeeklyDigest> = {}): WeeklyDigest {
  return {
    closedThisWeek: 41,
    proposals: 2,
    coveragePercent: 87,
    decisions: [proposal()],
    ...over,
  };
}

function draw(permissions: readonly string[] = ['DISCOVERY_CANVAS_VIEW', 'DISCOVERY_TYPE_CURATE']) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <DigestScreen permissions={permissions} />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('the weekly digest', () => {
  it('renders at most three decisions when more qualify, and never says how many waited', async () => {
    fetchDigest.mockResolvedValue(
      week({
        decisions: [
          proposal({
            typeId: 't1',
            subject: 'Account manager → Content writer',
            occurrenceCount: 40,
          }),
          proposal({ typeId: 't2', subject: 'Account manager → Designer', occurrenceCount: 12 }),
          proposal({ typeId: 't3', subject: 'Account manager → Media buyer', occurrenceCount: 8 }),
          proposal({ typeId: 't4', subject: 'Designer → Content writer', occurrenceCount: 6 }),
          proposal({
            typeId: 't5',
            subject: 'Content writer → Account manager',
            occurrenceCount: 5,
          }),
        ],
      }),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-decision').length).toBe(3));

    expect(container.textContent).not.toContain('Designer → Content writer');
    expect(container.textContent).not.toContain('Content writer → Account manager');
    expect(container.textContent).not.toMatch(/\+\s*2|2 more/);
  });

  it('says what it is waiting for rather than looking broken', async () => {
    fetchDigest.mockResolvedValue(week({ decisions: [] }));

    draw();

    await waitFor(() => expect(screen.getByText(en.discovery.digest.empty.heading)).toBeDefined());
    expect(screen.getByText(en.discovery.digest.empty.body)).toBeDefined();
  });

  it('leads with the three figures the week produced', async () => {
    fetchDigest.mockResolvedValue(week());

    const { container } = draw();

    await waitFor(() =>
      expect(container.querySelectorAll('.fo-disc-tiles .fo-stat').length).toBe(3),
    );

    const values = Array.from(container.querySelectorAll('.fo-stat-value')).map(
      (tile) => tile.textContent,
    );
    expect(values).toEqual(['41', '2', '87%']);

    const labels = Array.from(container.querySelectorAll('.fo-stat-label')).map(
      (tile) => tile.textContent,
    );
    expect(labels).toEqual([
      en.discovery.digest.tile.tracksClosed,
      en.discovery.digest.tile.proposals,
      en.discovery.digest.tile.coverage,
    ]);
  });

  it('renders no person name, given a response that carries none', async () => {
    fetchDigest.mockResolvedValue(
      week({
        decisions: [
          {
            ...proposal(),

            performerName: 'Andrei Munteanu',
            performerId: 'person-7',
            markedBy: 'Maria Ionescu',
          } as unknown as DigestDecision,
        ],
      }),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-decision').length).toBe(1));

    expect(container.textContent).toContain('Account manager → Content writer');
    expect(container.textContent).not.toContain('Andrei');
    expect(container.textContent).not.toContain('Maria');
    expect(container.textContent).not.toContain('person-7');
  });

  it('labels a provisional demotion and keys it to the role, offering nothing to confirm', async () => {
    fetchDigest.mockResolvedValue(
      week({
        decisions: [
          proposal(),
          {
            kind: 'A_ROLE_STOPPED_CLICKING',
            typeId: null,
            subject: 'Content writer',
            occurrenceCount: 0,
          },
        ],
      }),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-decision').length).toBe(2));

    const quiet = container.querySelectorAll('.fo-disc-decision')[1] as HTMLElement;
    expect(quiet.textContent).toContain('Content writer');
    expect(quiet.textContent).toContain(en.discovery.digest.provisional);
    expect(quiet.querySelector('[title]')?.getAttribute('title')).toBe(
      en.discovery.digest.provisionalNote,
    );

    expect(quiet.textContent).not.toContain('Repeated');
    expect(quiet.querySelector('input')).toBeNull();
    expect(quiet.querySelector('button')).toBeNull();

    expect(container.querySelectorAll('input').length).toBe(1);
  });

  it('says how often the shape repeated', async () => {
    fetchDigest.mockResolvedValue(week({ decisions: [proposal({ occurrenceCount: 5 })] }));

    const { container } = draw();

    await waitFor(() =>
      expect(container.querySelector('.fo-disc-decision__count')?.textContent).toBe(
        'Repeated 5 times',
      ),
    );
  });

  it('names a shape of work through the one field it offers, and says it landed', async () => {
    fetchDigest.mockResolvedValue(week());
    nameType.mockResolvedValue(undefined);

    draw();

    await waitFor(() => expect(screen.getByLabelText(en.discovery.type.name)).toBeDefined());

    fireEvent.change(screen.getByLabelText(en.discovery.type.name), {
      target: { value: 'Content writing' },
    });
    fireEvent.click(
      screen.getByRole('button', {
        name: 'Confirm Account manager → Content writer',
      }),
    );

    await waitFor(() => expect(nameType).toHaveBeenCalledWith('type-1', 'Content writing'));
    await waitFor(() =>
      expect(
        screen.getByText('Content writing is now a process the team can start.'),
      ).toBeDefined(),
    );
  });

  it('says why a name was refused and leaves the field live', async () => {
    fetchDigest.mockResolvedValue(week());
    nameType.mockRejectedValue(new ApiError(409, { code: 'TYPE_NAME_TAKEN', message: 'taken' }));

    draw();

    await waitFor(() => expect(screen.getByLabelText(en.discovery.type.name)).toBeDefined());
    fireEvent.change(screen.getByLabelText(en.discovery.type.name), {
      target: { value: 'Content writing' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^Confirm/ }));

    await waitFor(() => expect(nameType).toHaveBeenCalled());

    await waitFor(() => expect(screen.getByText(en.discovery.type.nameTaken)).toBeDefined());
    expect(screen.queryByText(en.discovery.error.UNKNOWN)).toBeNull();
    expect(screen.getByLabelText(en.discovery.type.name)).toBeDefined();
  });

  it('offers no name field to somebody the endpoint would refuse', async () => {
    fetchDigest.mockResolvedValue(week());

    const { container } = draw(['DISCOVERY_CANVAS_VIEW']);

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-decision').length).toBe(1));
    expect(container.textContent).toContain('Account manager → Content writer');
    expect(container.querySelector('input')).toBeNull();
    expect(screen.queryByRole('button', { name: /^Confirm/ })).toBeNull();
  });

  it('carries no badge, no unread dot and no mark-all-read', async () => {
    fetchDigest.mockResolvedValue(
      week({ decisions: [proposal({ typeId: 't1' }), proposal({ typeId: 't2' })] }),
    );

    const { container } = draw();

    await waitFor(() => expect(container.querySelectorAll('.fo-disc-decision').length).toBe(2));

    expect(container.querySelector('[class*="badge"]')).toBeNull();
    expect(container.querySelector('[class*="unread"]')).toBeNull();
    expect(container.querySelector('[class*="dot"]')).toBeNull();
    expect(container.textContent).not.toMatch(/mark all|see all/i);
  });
});
