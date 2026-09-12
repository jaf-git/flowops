// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { WorkSearchResult } from '../api/jobGraphApi';
import { WorkSearch } from './WorkSearch';

const searchWork = vi.fn<(query: string) => Promise<WorkSearchResult>>();

vi.mock('../api/jobGraphApi', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  searchWork: (query: string) => searchWork(query),
}));

function result(over: Partial<WorkSearchResult> = {}): WorkSearchResult {
  return {
    matches: [
      {
        nodeId: 'n-1',
        bracketId: 'b-1',
        jobId: 'job-1',
        conversationId: 'c-1',
        workType: 'DESIGN',
        text: 'the poster needs the new logo',
      },
    ],
    beyondReach: 0,
    ...over,
  };
}

function renderSearch(over: Partial<Parameters<typeof WorkSearch>[0]> = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <WorkSearch {...over} />
    </QueryClientProvider>,
  );
}

async function searchFor(term: string): Promise<void> {
  fireEvent.change(screen.getByLabelText(/search the work/i), { target: { value: term } });
  await waitFor(() => {
    expect(searchWork).toHaveBeenCalledWith(term);
  });
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  searchWork.mockReset().mockResolvedValue(result());
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
});

describe('the third answer', () => {
  it('says how many results are in rooms the reader is not in', async () => {
    searchWork.mockResolvedValue(result({ beyondReach: 4 }));
    renderSearch();

    await searchFor('logo');

    expect(
      await screen.findByText('4 more results are in conversations you are not in.'),
    ).toBeTruthy();
  });

  it('says it in the singular when there is one', async () => {
    searchWork.mockResolvedValue(result({ beyondReach: 1 }));
    renderSearch();

    await searchFor('logo');

    expect(
      await screen.findByText('1 more result is in a conversation you are not in.'),
    ).toBeTruthy();
  });

  it('says nothing at all when there is nothing out of reach', async () => {
    renderSearch();

    await searchFor('logo');

    expect(await screen.findByText(/the poster needs the new logo/)).toBeTruthy();
    expect(screen.queryByText(/are in conversations you are not in/)).toBeNull();
    expect(screen.queryByText(/^0 /)).toBeNull();
  });

  it('does not claim nothing was found when everything found is out of reach', async () => {
    searchWork.mockResolvedValue({ matches: [], beyondReach: 3 });
    renderSearch();

    await searchFor('invoice');

    expect(
      await screen.findByText(/3 more results are in conversations you are not in/),
    ).toBeTruthy();
    expect(screen.queryByText(/Nothing marked as work mentions/)).toBeNull();
  });
});

describe('what it asks, and when', () => {
  it('asks nothing until there are two letters', async () => {
    renderSearch();

    fireEvent.change(screen.getByLabelText(/search the work/i), { target: { value: 'l' } });
    await new Promise((settle) => setTimeout(settle, 400));

    expect(searchWork).not.toHaveBeenCalled();
    expect(screen.getByText(/Two letters or more/)).toBeTruthy();
  });

  it('asks once for a word typed in one go, not once per keystroke', async () => {
    renderSearch();
    const field = screen.getByLabelText(/search the work/i);

    fireEvent.change(field, { target: { value: 'lo' } });
    fireEvent.change(field, { target: { value: 'log' } });
    fireEvent.change(field, { target: { value: 'logo' } });

    await waitFor(() => {
      expect(searchWork).toHaveBeenCalledWith('logo');
    });

    expect(searchWork).toHaveBeenCalledTimes(1);
  });

  it('does not report an empty workspace before anybody has asked', () => {
    renderSearch();

    expect(screen.queryByText(/Nothing marked as work mentions/)).toBeNull();
    expect(screen.getByText(/Two letters or more/)).toBeTruthy();
  });
});

describe('what a result offers', () => {
  it('shows the sentence and the kind of work it became', async () => {
    renderSearch();

    await searchFor('logo');

    expect(await screen.findByText('the poster needs the new logo')).toBeTruthy();
    expect(screen.getByText('DESIGN')).toBeTruthy();
  });

  it('opens the room and the sentence it was said in', async () => {
    const opened = vi.fn();
    renderSearch({ onOpenMessage: opened });

    await searchFor('logo');
    fireEvent.click(await screen.findByRole('button', { name: 'Open' }));

    expect(opened).toHaveBeenCalledWith('c-1', 'n-1');
  });

  it('offers no way to search by person', async () => {
    renderSearch();

    await searchFor('logo');

    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.queryByLabelText(/who|person|performer|colleague/i)).toBeNull();
  });

  it('says what happened when the search itself fails, and where the words still are', async () => {
    searchWork.mockRejectedValue(new Error('nope'));
    renderSearch();

    await searchFor('logo');

    expect(await screen.findByText(/could not be run/)).toBeTruthy();
  });
});
