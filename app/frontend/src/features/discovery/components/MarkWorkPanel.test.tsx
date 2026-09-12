// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState, type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { EarlierWorkHere } from '../api/workApi';
import { MarkWorkPanel, type MarkAddress } from './MarkWorkPanel';

const apiRequest = vi.fn<(path: string, init?: RequestInit) => Promise<unknown>>();

vi.mock('../../../shared/api/client', () => ({
  apiRequest: (path: string, init?: RequestInit) => apiRequest(path, init),
  ApiError: class extends Error {},
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, unknown>) =>
      values === undefined ? key : `${key} ${JSON.stringify(values)}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

const A_DAY = 86_400_000;

const daysAgo = (days: number): string => new Date(Date.now() - days * A_DAY).toISOString();

const CAPTION = {
  id: 'a-1',
  name: 'Write the caption',
  status: 'ACTIVE' as const,
  slug: 'write-the-caption',
  timesUsed: 23,
  lastUsedAt: daysAgo(200),
  departments: ['Copy'],
  counterparties: ['Aurora Coffee'],
  tooGenericToBeOneThing: false,
};

const SHOT_LIST = {
  id: 'a-2',
  name: 'Shot list',
  status: 'ACTIVE' as const,
  slug: 'shot-list',
  timesUsed: 9,
  lastUsedAt: daysAgo(200),
  departments: ['Design'],
  counterparties: ['Aurora Coffee'],
  tooGenericToBeOneThing: false,
};

const PUBLISH = {
  id: 'a-3',
  name: 'Publish',
  status: 'ACTIVE' as const,
  slug: 'publish',
  timesUsed: 2,
  lastUsedAt: daysAgo(200),
  departments: [],
  counterparties: [],
  tooGenericToBeOneThing: false,
};

const BRAND_CHECK = {
  id: 'a-4',
  name: 'Brand check',
  status: 'ACTIVE' as const,
  slug: 'brand-check',
  timesUsed: 1,
  lastUsedAt: daysAgo(1),
  departments: ['Strategy'],
  counterparties: ['Hanul Verde'],
  tooGenericToBeOneThing: false,
};

const CAPTION_SET = {
  id: 'a-9',
  name: 'Caption set',
  status: 'ACTIVE' as const,
  slug: 'caption-set',
  timesUsed: 0,
  lastUsedAt: null,
  departments: [],
  counterparties: [],
  tooGenericToBeOneThing: false,
};

function serve(over: { activities?: unknown[]; named?: unknown } = {}): void {
  apiRequest.mockImplementation((path, init) => {
    if (path === '/discovery/activities' && init?.method === 'POST') {
      return Promise.resolve(over.named ?? CAPTION_SET);
    }

    if (path === '/discovery/activities') {
      return Promise.resolve(over.activities ?? [CAPTION, SHOT_LIST, PUBLISH]);
    }

    if (path.startsWith('/discovery/graph/work-type')) {
      return Promise.resolve({ workType: 'CONTENT', neverUsedBefore: false, closestExisting: [] });
    }

    return Promise.resolve([]);
  });
}

let cache: QueryClient;
let address: MarkAddress;
let changed: MarkAddress[];

interface Situation {
  readonly workType: string;
  readonly clientName?: string | null;
  readonly earlierWorkHere?: EarlierWorkHere | null;
}

function Panel({ workType, clientName, earlierWorkHere }: Situation): ReactElement {
  const [current, setCurrent] = useState<MarkAddress>(address);

  return (
    <MarkWorkPanel
      conversationId="c-1"
      address={current}
      derivedWorkType={workType}
      onChange={(next) => {
        address = next;
        changed.push(next);
        setCurrent(next);
      }}
      people={[{ id: 'p-1', displayName: 'Cristina' }]}
      everybody={[{ id: 'p-1', displayName: 'Cristina' }]}
      onBringIn={() => Promise.resolve()}
      clientName={clientName ?? null}
      earlierWorkHere={earlierWorkHere ?? null}
    />
  );
}

function draw(workType = 'GENERAL', situation: Omit<Situation, 'workType'> = {}): ReactElement {
  return (
    <QueryClientProvider client={cache}>
      <Panel workType={workType} {...situation} />
    </QueryClientProvider>
  );
}

function activityField(): HTMLInputElement {
  return screen.getByLabelText('discovery.activity.label') as HTMLInputElement;
}

async function type(what: string): Promise<void> {
  fireEvent.change(activityField(), { target: { value: what } });

  await waitFor(() => {
    expect(activityField().value).toBe(what);
  });
}

function suggested(): string[] {
  const list = screen.queryByRole('group', { name: 'discovery.activity.suggestions' });

  return list === null
    ? []
    : [...list.querySelectorAll('.fo-mark-activity-name')].map((one) => one.textContent ?? '');
}

async function listed(): Promise<void> {
  await screen.findByRole('group', { name: 'discovery.activity.suggestions' });
}

beforeEach(() => {
  apiRequest.mockReset();
  cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  address = { jobId: 'job-1', performerId: 'p-1', workType: null, activityId: null };
  changed = [];
});

afterEach(cleanup);

describe('the catalogue arrives once and is read in the browser', () => {
  it('asks for the whole list rather than searching for what was typed', async () => {
    serve();
    render(draw());

    await listed();

    await type('cap');
    await type('capt');
    await type('captio');

    const asked = apiRequest.mock.calls.filter(([path]) =>
      path.startsWith('/discovery/activities'),
    );

    expect(asked).toHaveLength(1);
    expect(asked[0]?.[0]).toBe('/discovery/activities');
    expect(asked[0]?.[1]?.method).toBeUndefined();
  });

  it('shows what the workspace uses most first, before anybody types', async () => {
    serve();
    render(draw());

    await waitFor(() => {
      expect(suggested()).toEqual(['Write the caption', 'Shot list', 'Publish']);
    });
  });

  it('floats what was used in the last week above what was used more often', async () => {
    serve({ activities: [CAPTION, SHOT_LIST, PUBLISH, BRAND_CHECK] });
    render(draw());

    await waitFor(() => {
      expect(suggested()[0]).toBe('Brand check');
    });
  });
});

describe('typing filters, and every suggestion says where it comes from', () => {
  it('narrows by prefix, then by substring', async () => {
    serve();
    render(draw());
    await listed();

    await type('shot');
    await waitFor(() => {
      expect(suggested()).toEqual(['Shot list']);
    });

    await type('caption');
    await waitFor(() => {
      expect(suggested()).toEqual(['Write the caption']);
    });
  });

  it('still finds an activity the person mistyped', async () => {
    serve();
    render(draw());
    await listed();

    await type('shpt lst');

    await waitFor(() => {
      expect(suggested()).toContain('Shot list');
    });
  });

  it('names the department when it is the one this performer works in', async () => {
    serve({ activities: [SHOT_LIST] });
    render(draw('DESIGN'));

    await screen.findByText('discovery.activity.byDepartment {"department":"Design","count":9}');
  });

  it('says how recently it was used when that is what puts it first', async () => {
    serve({ activities: [BRAND_CHECK] });
    render(draw());

    await screen.findByText('discovery.activity.yesterday');
  });

  it('falls back to the workspace count when neither applies', async () => {
    serve({ activities: [PUBLISH] });
    render(draw());

    await screen.findByText('discovery.activity.acrossWorkspace {"count":2}');
  });

  it('warns on an activity broad enough to have stopped naming one thing', async () => {
    serve({ activities: [{ ...PUBLISH, tooGenericToBeOneThing: true }] });
    render(draw());

    await screen.findByText('discovery.activity.tooGeneric');
  });

  it('carries the chosen activity out to the mark', async () => {
    serve();
    render(draw());

    await type('shot');

    fireEvent.click(await screen.findByRole('button', { name: /Shot list/ }));

    await waitFor(() => {
      expect(address.activityId).toBe('a-2');
    });
  });
});

describe('the near miss asks and never merges', () => {
  it('offers the activity it resembles without applying it', async () => {
    serve();
    render(draw());
    await listed();

    await type('Write the captions');

    await screen.findByText('discovery.activity.nearMiss {"name":"Write the caption","count":23}');
    await screen.findByText('discovery.activity.nearMissMostly {"department":"Copy"}');

    expect(address.activityId).toBeNull();
    expect(changed.some((one) => one.activityId !== null)).toBe(false);
  });

  it('applies it only when the person presses the offer', async () => {
    serve();
    render(draw());

    await type('Write the captions');

    fireEvent.click(await screen.findByRole('button', { name: 'discovery.activity.nearMissUse' }));

    await waitFor(() => {
      expect(address.activityId).toBe('a-1');
    });
  });

  it('withdraws the offer when the person says theirs is different, and merges nothing', async () => {
    serve();
    render(draw());

    await type('Write the captions');

    fireEvent.click(
      await screen.findByRole('button', { name: 'discovery.activity.nearMissDifferent' }),
    );

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'discovery.activity.nearMissUse' })).toBeNull();
    });

    expect(address.activityId).toBeNull();

    await screen.findByRole('button', { name: /discovery.activity.name/ });
  });

  it('names a new activity only when asked, and then chooses it', async () => {
    serve();
    render(draw());

    await type('Caption set');

    expect(screen.queryByRole('button', { name: 'discovery.activity.nearMissUse' })).toBeNull();

    fireEvent.click(await screen.findByRole('button', { name: /discovery.activity.name/ }));

    await waitFor(() => {
      expect(address.activityId).toBe('a-9');
    });

    const posted = apiRequest.mock.calls.find(
      ([path, init]) => path === '/discovery/activities' && init?.method === 'POST',
    );

    expect(JSON.parse((posted?.[1]?.body as string | undefined) ?? '{}')).toEqual({
      name: 'Caption set',
    });
  });
});

describe('a suggestion carries the client whose work it was used on', () => {
  it('says whose work it was used on when this engagement is for them', async () => {
    serve({ activities: [SHOT_LIST] });
    render(draw('CONTENT', { clientName: 'Aurora Coffee' }));

    await screen.findByText('discovery.activity.onClientWork {"client":"Aurora Coffee","count":9}');
  });

  it('keeps the department above the client, because the department is the closer scope', async () => {
    serve({ activities: [SHOT_LIST] });
    render(draw('DESIGN', { clientName: 'Aurora Coffee' }));

    await screen.findByText('discovery.activity.byDepartment {"department":"Design","count":9}');
  });
});

describe('the prompt fires at the moment of ambiguity and forces nothing', () => {
  it('names who already did work of this kind in this engagement, and asks what is different', async () => {
    serve();
    render(
      draw('CONTENT', {
        earlierWorkHere: {
          workType: 'CONTENT',
          people: ['Cristina'],
          activities: ['Write the caption'],
        },
      }),
    );

    await screen.findByText('discovery.activity.ambiguityWho {"who":"Cristina","kind":"CONTENT"}');
    await screen.findByText('discovery.activity.ambiguityAsk');
    await screen.findByText('discovery.activity.ambiguityAlready {"names":"Write the caption"}');

    expect(activityField().required).toBe(false);
    expect(address.activityId).toBeNull();
  });

  it('speaks of the engagement rather than one person when several have', async () => {
    serve();
    render(
      draw('CONTENT', {
        earlierWorkHere: { workType: 'CONTENT', people: ['Cristina', 'Radu'], activities: [] },
      }),
    );

    await screen.findByText('discovery.activity.ambiguityHere {"kind":"CONTENT"}');
  });

  it('stops asking the moment the person starts naming one', async () => {
    serve();
    render(
      draw('CONTENT', {
        earlierWorkHere: { workType: 'CONTENT', people: ['Cristina'], activities: [] },
      }),
    );

    await screen.findByText('discovery.activity.ambiguityWho {"who":"Cristina","kind":"CONTENT"}');

    await type('Caption');

    await waitFor(() => {
      expect(
        screen.queryByText('discovery.activity.ambiguityWho {"who":"Cristina","kind":"CONTENT"}'),
      ).toBeNull();
    });
  });

  it('says nothing when this kind of work is new to the engagement', async () => {
    serve();
    render(draw('CONTENT'));
    await listed();

    expect(screen.queryByText('discovery.activity.ambiguityAsk')).toBeNull();
  });
});

describe('a model may suggest a near miss it shares no characters with, and never merges', () => {
  it('offers what the model matched, says a model said it, and applies nothing', async () => {
    serve({ activities: [CAPTION] });
    apiRequest.mockImplementation((path, init) => {
      if (path === '/discovery/activities' && init?.method === undefined) {
        return Promise.resolve([CAPTION]);
      }

      if (path === '/node-pipeline/activities/near-miss') {
        return Promise.resolve({
          same: 'Write the caption',
          confidence: 0.8,
          reason: 'both name the words under a post',
          modelId: 'llama3.2:3b',
        });
      }

      return Promise.resolve([]);
    });

    render(draw());
    await type('Post copy');

    await screen.findByText(
      'discovery.activity.nearMiss {"name":"Write the caption","count":23}',
      undefined,
      { timeout: 3000 },
    );
    await screen.findByText('discovery.activity.nearMissByModel');

    expect(address.activityId).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'discovery.activity.nearMissUse' }));

    await waitFor(() => {
      expect(address.activityId).toBe('a-1');
    });
  });

  it('asks the model nothing while the browser can answer from characters alone', async () => {
    serve();
    render(draw());
    await listed();

    await type('caption');

    await waitFor(() => {
      expect(suggested()).toEqual(['Write the caption']);
    });

    expect(
      apiRequest.mock.calls.filter(([path]) => path === '/node-pipeline/activities/near-miss'),
    ).toEqual([]);
  });
});

describe('the field is optional', () => {
  it('never names an activity on its own', async () => {
    serve();
    render(draw());
    await listed();

    await type('Write the captions');

    expect(address.activityId).toBeNull();
    expect(activityField().required).toBe(false);
    expect(activityField().getAttribute('aria-invalid')).toBeNull();
    expect(changed).toEqual([]);
  });

  it('survives a workspace that has named nothing yet', async () => {
    serve({ activities: [] });
    render(draw());

    await waitFor(() => {
      expect(apiRequest).toHaveBeenCalledWith('/discovery/activities', undefined);
    });

    expect(suggested()).toEqual([]);
    expect(screen.queryByRole('button', { name: /discovery.activity.name/ })).toBeNull();
    expect(address.activityId).toBeNull();
  });
});
