// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { fireEvent } from '@testing-library/react';
import type { JSX, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { WorkVocabularyProvider } from '../../../shared/model/WorkVocabularyProvider';
import type { WorkLineage, WorkVocabulary } from '../../../shared/model/workVocabulary';
import { TemplateLineage } from './TemplateLineage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${JSON.stringify(options)}`,
    i18n: { language: 'en' },
  }),
}));

const lineageOf = vi.fn();
const vocabulary: WorkVocabulary = {
  listApproved: () => Promise.resolve([]),
  lineageOf: (id: string) => lineageOf(id) as Promise<WorkLineage>,

  originOf: () => Promise.resolve(null),
  analysisOf: () =>
    Promise.resolve({ stamped: 0, medianActiveSeconds: null, passedFirstTime: 0, reviewed: 0 }),
  observationsOn: () => Promise.resolve([]),
  entryOf: () => Promise.resolve({ title: '', draft: false }),
};

afterEach(() => {
  cleanup();
  lineageOf.mockReset();
});

function draw(uses: WorkLineage, onOpenTask?: (taskId: string) => void): JSX.Element {
  lineageOf.mockResolvedValue(uses);

  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }): JSX.Element => (
    <QueryClientProvider client={client}>
      <WorkVocabularyProvider vocabulary={vocabulary}>
        <MemoryRouter>{children}</MemoryRouter>
      </WorkVocabularyProvider>
    </QueryClientProvider>
  );
  return render(<TemplateLineage taskTemplateId="tt-1" locale="en" onOpenTask={onOpenTask} />, {
    wrapper,
  }).container as unknown as JSX.Element;
}

function uses(overrides: Partial<WorkLineage> = {}): WorkLineage {
  return {
    processTemplates: [
      { templateId: 'p-1', name: 'Brand strategy sprint', position: 5, active: true },
      { templateId: 'p-2', name: 'Quarterly brand audit', position: 4, active: false },
    ],
    runs: [
      {
        instanceId: 'i-1',
        instanceName: 'Henderson — sprint',
        instanceState: 'RUNNING',
        stepId: 's-1',
        stepCondition: 'ASSIGNED',
        taskId: 't-1',
        startedAt: '2026-08-01T09:00:00Z',
      },
      {
        instanceId: 'i-2',
        instanceName: 'Atelier — sprint',
        instanceState: 'COMPLETE',
        stepId: 's-2',
        stepCondition: 'REACHABLE',
        taskId: null,
        startedAt: '2026-07-01T09:00:00Z',
      },
    ],
    runsTotal: 2,
    ...overrides,
  };
}

describe('the lineage panel', () => {
  it('names the processes that plan this work, and where in each it sits', async () => {
    draw(uses());

    await waitFor(() => {
      expect(screen.getByText('Brand strategy sprint')).toBeTruthy();
    });
    expect(screen.getByText('Quarterly brand audit')).toBeTruthy();
    expect(screen.getByText('tasklib.lineage.atStep {"position":5}')).toBeTruthy();
  });

  it('keeps a retired process and marks it retired rather than dropping it', async () => {
    draw(uses());

    await waitFor(() => {
      expect(screen.getByText('Quarterly brand audit')).toBeTruthy();
    });
    expect(screen.getByText('tasklib.lineage.retired')).toBeTruthy();
  });

  it('links each run to its canvas', async () => {
    draw(uses());

    await waitFor(() => {
      expect(screen.getByText('Henderson — sprint')).toBeTruthy();
    });
    expect(screen.getByText('Henderson — sprint').getAttribute('href')).toBe(
      '/en/canvas/process/i-1',
    );
  });

  it('opens a task through the host, and offers nothing where there is no task yet', async () => {
    const opened = vi.fn();
    draw(uses(), opened);

    await waitFor(() => {
      expect(screen.getByText('tasklib.lineage.openTask')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('tasklib.lineage.openTask'));
    expect(opened).toHaveBeenCalledWith('t-1');

    expect(screen.getByText('tasklib.lineage.noTaskYet')).toBeTruthy();
    expect(screen.getAllByText('tasklib.lineage.openTask')).toHaveLength(1);
  });

  it('reports the real total rather than the number of rows it drew', async () => {
    draw(uses({ runsTotal: 412 }));

    await waitFor(() => {
      expect(screen.getByText('tasklib.lineage.cutIn {"count":412}')).toBeTruthy();
    });
    expect(screen.getByText('tasklib.lineage.showingSome {"shown":2,"total":412}')).toBeTruthy();
  });

  it('says plainly when nothing uses this work', async () => {
    draw(uses({ processTemplates: [], runs: [], runsTotal: 0 }));

    await waitFor(() => {
      expect(screen.getByText('tasklib.lineage.nothingUsesThis')).toBeTruthy();
    });
  });

  it('renders nobody, because a run is named by its work and never by who holds it', async () => {
    const container = draw(uses()) as unknown as HTMLElement;

    await waitFor(() => {
      expect(screen.getByText('Henderson — sprint')).toBeTruthy();
    });
    expect(container.textContent).not.toMatch(/assignee|owner|Maria|Andrei/i);
  });
});
