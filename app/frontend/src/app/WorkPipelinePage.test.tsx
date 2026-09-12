// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { JSX, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { WorkVocabularyProvider } from '../shared/model/WorkVocabularyProvider';
import type { WorkVocabulary } from '../shared/model/workVocabulary';
import { WorkPipelinePage } from './WorkPipelinePage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined || options.title === undefined
        ? key
        : `${key}:${String(options.title)}`,
    i18n: { language: 'en' },
  }),
}));

const detailFor: Record<string, { id: string; title: string; state: string; templateId: string }> =
  {
    'task-1': { id: 'task-1', title: 'Curăță atelierul', state: 'CREATED', templateId: 'tpl-1' },
    'task-2': { id: 'task-2', title: 'Verifică facturile', state: 'CLOSED', templateId: 'tpl-2' },
  };

let opened: string | undefined;

let queue: { data?: { tasks: unknown[] }; isPending: boolean; isError: boolean } = {
  data: {
    tasks: [
      { id: 'task-1', title: 'Curăță atelierul', state: 'CREATED' },
      { id: 'task-2', title: 'Verifică facturile', state: 'CLOSED' },
    ],
  },
  isPending: false,
  isError: false,
};

vi.mock('../features/task', () => ({
  useTasks: () => queue,
  useTaskDetail: (id: string | undefined) => ({
    data: id === undefined ? undefined : detailFor[id],
    isError: false,
  }),
}));

const vocabulary: WorkVocabulary = {
  listApproved: () => Promise.resolve([]),
  originOf: () => Promise.resolve(null),
  entryOf: (id: string) =>
    Promise.resolve({ title: id === 'tpl-1' ? 'Curățenie săptămânală' : 'Facturi', draft: false }),
  analysisOf: () =>
    Promise.resolve({ stamped: 0, medianActiveSeconds: null, passedFirstTime: 0, reviewed: 0 }),
  lineageOf: () => Promise.resolve({ processTemplates: [], runs: [], runsTotal: 0 }),
  observationsOn: () => Promise.resolve([]),
};

afterEach(() => {
  opened = undefined;
  queue = {
    data: {
      tasks: [
        { id: 'task-1', title: 'Curăță atelierul', state: 'CREATED' },
        { id: 'task-2', title: 'Verifică facturile', state: 'CLOSED' },
      ],
    },
    isPending: false,
    isError: false,
  };
  cleanup();
});

function draw(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }): JSX.Element => (
    <QueryClientProvider client={client}>
      <WorkVocabularyProvider vocabulary={vocabulary}>
        <MemoryRouter>{children}</MemoryRouter>
      </WorkVocabularyProvider>
    </QueryClientProvider>
  );
  render(
    <WorkPipelinePage
      locale="en"
      onOpenTask={(id) => {
        opened = id;
      }}
    />,
    { wrapper },
  );
}

describe('the work pipeline page', () => {
  it('invites a choice before one is made', () => {
    draw();

    expect(screen.getByText('pipeline.page.nothingChosen')).toBeTruthy();
  });

  it('draws the chain of the task that was chosen, and swaps it when another is', async () => {
    draw();

    fireEvent.click(screen.getByText('Curăță atelierul'));
    await waitFor(() =>
      expect(screen.getByText('pipeline.template.approved:Curățenie săptămânală')).toBeTruthy(),
    );

    fireEvent.click(screen.getByText('Verifică facturile'));
    await waitFor(() =>
      expect(screen.getByText('pipeline.template.approved:Facturi')).toBeTruthy(),
    );
    expect(screen.queryByText('pipeline.template.approved:Curățenie săptămânală')).toBeNull();
  });

  it('sends a stage to the library entry whose evidence it is', async () => {
    draw();

    fireEvent.click(screen.getByText('Curăță atelierul'));
    const link = await screen.findByText('pipeline.page.evidence');

    expect(link.getAttribute('href')).toBe('/en/templates/tpl-1');
  });

  it('does not call an unread queue empty', () => {
    queue = { data: undefined, isPending: true, isError: false };

    draw();

    expect(screen.getByText('pipeline.page.loading')).toBeTruthy();
    expect(screen.queryByText('pipeline.page.noWork')).toBeNull();
    expect(screen.queryByText('pipeline.page.cannotList')).toBeNull();
  });

  it('says the queue could not be read when it could not', () => {
    queue = { data: undefined, isPending: false, isError: true };

    draw();

    expect(screen.getByText('pipeline.page.cannotList')).toBeTruthy();
    expect(screen.queryByText('pipeline.page.noWork')).toBeNull();
  });

  it('opens the task it is describing', () => {
    draw();

    fireEvent.click(screen.getByText('Curăță atelierul'));
    fireEvent.click(screen.getByText('pipeline.page.openTask'));

    expect(opened).toBe('task-1');
  });
});
