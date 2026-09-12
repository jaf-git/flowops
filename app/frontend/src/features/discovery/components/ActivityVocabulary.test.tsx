// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ActivityVocabulary } from './ActivityVocabulary';

const fetchActivities = vi.fn();
const mergeActivity = vi.fn();
const retireActivity = vi.fn();

vi.mock('../api/bracketApi', () => ({
  fetchActivities: () => fetchActivities(),
  mergeActivity: (from: string, into: string) => mergeActivity(from, into),
  retireActivity: (id: string) => retireActivity(id),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, unknown>) =>
      values === undefined ? key : `${key} ${JSON.stringify(values)}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

const CAPTION = {
  id: 'a-1',
  name: 'Write the caption',
  status: 'ACTIVE',
  slug: 'write-the-caption',
  timesUsed: 23,
  lastUsedAt: null,
  departments: ['Copy'],
  counterparties: ['Aurora Coffee'],
  tooGenericToBeOneThing: false,
};

const COPY = {
  id: 'a-2',
  name: 'Post copy',
  status: 'ACTIVE',
  slug: 'post-copy',
  timesUsed: 4,
  lastUsedAt: null,
  departments: ['Copy'],
  counterparties: [],
  tooGenericToBeOneThing: false,
};

let cache: QueryClient;

function draw(): void {
  render(
    <QueryClientProvider client={cache}>
      <ActivityVocabulary />
    </QueryClientProvider>,
  );
}

function named(): string[] {
  return [...document.querySelectorAll('.fo-vocabulary-word .fo-vocabulary-name')].map(
    (one) => one.textContent ?? '',
  );
}

async function mergeControlFor(name: string): Promise<HTMLSelectElement> {
  return (await screen.findByLabelText(
    `discovery.vocabulary.mergeInto {"name":"${name}"}`,
  )) as HTMLSelectElement;
}

beforeEach(() => {
  cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  fetchActivities.mockReset().mockResolvedValue([CAPTION, COPY]);
  mergeActivity.mockReset().mockResolvedValue(CAPTION);
  retireActivity.mockReset().mockResolvedValue({ ...COPY, status: 'RETIRED' });
});

afterEach(cleanup);

describe('the vocabulary a person can put right', () => {
  it('lists every activity with what it has been used on', async () => {
    draw();

    await screen.findByText(/discovery.vocabulary.used {"count":23}.*Copy.*Aurora Coffee/);

    expect(named()).toEqual(['Write the caption', 'Post copy']);
  });

  it('says nothing at all when the workspace has named nothing', async () => {
    fetchActivities.mockResolvedValue([]);
    draw();

    await waitFor(() => {
      expect(fetchActivities).toHaveBeenCalled();
    });

    expect(screen.queryByText(/discovery.vocabulary.count/)).toBeNull();
  });

  it('merges only after a person picks a survivor and presses', async () => {
    draw();

    fireEvent.change(
      await screen.findByLabelText('discovery.vocabulary.mergeInto {"name":"Post copy"}'),
      {
        target: { value: 'a-1' },
      },
    );

    expect(mergeActivity).not.toHaveBeenCalled();

    fireEvent.click(
      await screen.findByRole('button', {
        name: 'discovery.vocabulary.merge {"name":"Write the caption"}',
      }),
    );

    await waitFor(() => {
      expect(mergeActivity).toHaveBeenCalledWith('a-2', 'a-1');
    });
  });

  it('never offers an activity as its own survivor', async () => {
    draw();

    const control = await mergeControlFor('Post copy');

    expect([...control.options].map((one) => one.value)).toEqual(['', 'a-1']);
  });

  it('retires one only on a second press, and never touches the merge picker', async () => {
    draw();

    const buttons = await screen.findAllByRole('button', { name: 'discovery.vocabulary.retire' });
    fireEvent.click(buttons[0] as HTMLButtonElement);

    expect(retireActivity).not.toHaveBeenCalled();

    fireEvent.click(
      await screen.findByRole('button', { name: 'discovery.vocabulary.retireConfirm' }),
    );

    await waitFor(() => {
      expect(retireActivity).toHaveBeenCalledWith('a-1');
    });

    expect(mergeActivity).not.toHaveBeenCalled();
  });

  it('says so and changes nothing on screen when the merge is refused', async () => {
    mergeActivity.mockRejectedValue(new Error('ACTIVITY_MERGE_REFUSED'));
    draw();

    fireEvent.change(
      await screen.findByLabelText('discovery.vocabulary.mergeInto {"name":"Post copy"}'),
      {
        target: { value: 'a-1' },
      },
    );

    fireEvent.click(
      await screen.findByRole('button', {
        name: 'discovery.vocabulary.merge {"name":"Write the caption"}',
      }),
    );

    await screen.findByText('discovery.vocabulary.failed');

    expect(named()).toEqual(['Write the caption', 'Post copy']);
  });
});
