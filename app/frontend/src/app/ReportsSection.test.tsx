// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import type { JSX, ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ReportsSection } from './App';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const asked: string[] = [];
let answer = { instances: [] as unknown[], isPending: false, incomplete: false };

vi.mock('../features/process', () => ({
  useInstancesInFull: (population: string) => {
    asked.push(population);
    return answer;
  },
}));

vi.mock('../features/reports', () => ({
  ReportsScreen: (props: { loading: boolean; incomplete?: boolean }) => (
    <p>{`reports loading=${props.loading} incomplete=${String(props.incomplete)}`}</p>
  ),
  summarise: () => ({ activeRuns: 0, runs: 0 }),
}));

vi.mock('../features/task', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../features/task')>()),
  fetchThroughput: () => Promise.resolve([]),
}));

afterEach(() => {
  asked.length = 0;
  answer = { instances: [], isPending: false, incomplete: false };
  cleanup();
});

function draw(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }): JSX.Element => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  render(<ReportsSection locale="en" />, { wrapper });
}

describe('the reports section', () => {
  it('computes its figures over every run, including the ones somebody put away', () => {
    draw();

    expect(asked).toEqual(['EVERY_RUN']);
    expect(asked).not.toContain('ON_THE_BOARD');
  });

  it('passes on that some runs could not be read', () => {
    answer = { instances: [], isPending: false, incomplete: true };

    draw();

    expect(screen.getByText(/incomplete=true/)).toBeTruthy();
  });
});
