// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NoticeProvider } from '../../../shared/notice/NoticeProvider';
import type { TaskTemplate } from '../api/taskTemplateApi';
import { TemplatePreviewDialog } from './TemplatePreviewDialog';

const fetchTemplate = vi.fn<(id: string) => Promise<TaskTemplate>>();
const stamp = vi.fn();

vi.mock('../api/taskTemplateApi', () => ({
  fetchTemplate: (id: string) => fetchTemplate(id),
}));

vi.mock('../hooks/useTaskTemplates', () => ({
  useStampTask: () => ({ mutate: stamp, isPending: false, isError: false, error: undefined }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${Object.values(options).join(' ')}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function template(over: Partial<TaskTemplate> = {}): TaskTemplate {
  return {
    id: 'tpl-1',
    title: 'Monthly write-up — October Iulius',
    description:
      'Scheduling and reporting does this work. Drafted from 124 pieces of work across 37 engagements.',
    type: null,
    priority: 'NORMAL',
    estimatedHours: null,
    checklist: ['Pull the month’s figures', 'Write the summary', 'Send to the client'],
    status: 'APPROVED',
    authorId: 'p1',
    rejectionReason: null,
    convertedToProcessTemplateId: null,
    processShapeHints: [],
    metadata: {
      responsibleRole: 'Scheduling and reporting',
      triggerNote: null,
      requiredInput: null,
      expectedOutput: null,
      outputKind: null,
      completionCriteria: null,
      nextAsk: null,
    } as TaskTemplate['metadata'],
    timesUsed: 0,
    createdAt: '2026-09-03T17:48:00Z',
    updatedAt: '2026-09-04T09:00:00Z',
    discoveredByPipeline: true,
    ...over,
  };
}

const ANA = { id: 'ana', name: 'Ana Moldovan' };

function draw(
  open = true,
  withWhom: { id: string; name: string } | null = ANA,
  onClose = () => {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <NoticeProvider>
        <TemplatePreviewDialog
          templateId="tpl-1"
          open={open}
          onClose={onClose}
          withWhom={withWhom}
        />
      </NoticeProvider>
    </QueryClientProvider>,
  );
}

describe('previewing a template without leaving the conversation', () => {
  it('shows the checklist in full', async () => {
    fetchTemplate.mockResolvedValue(template());

    draw();

    await waitFor(() => expect(screen.getByText('Write the summary')).toBeDefined());
    expect(screen.getByText('Pull the month’s figures')).toBeDefined();
    expect(screen.getByText('Send to the client')).toBeDefined();
  });

  it('says when the pipeline drafted it', async () => {
    fetchTemplate.mockResolvedValue(template());

    draw();

    await waitFor(() => expect(screen.getByText('tasklib.card.fromThePipeline')).toBeDefined());
  });

  it('does not claim the pipeline drafted one a person wrote', async () => {
    fetchTemplate.mockResolvedValue(template({ discoveredByPipeline: false }));

    draw();

    await waitFor(() => expect(screen.getByText('Write the summary')).toBeDefined());
    expect(screen.queryByText('tasklib.card.fromThePipeline')).toBeNull();
  });

  it('omits the steps entirely where nobody wrote any', async () => {
    fetchTemplate.mockResolvedValue(template({ checklist: [] }));

    draw();

    await waitFor(() => expect(screen.getByText(/Scheduling and reporting does/)).toBeDefined());
    expect(screen.queryByText(/tasklib\.preview\.steps/)).toBeNull();
  });

  it('asks for nothing until it is opened', () => {
    draw(false);

    expect(fetchTemplate).not.toHaveBeenCalled();
  });

  it('offers the work to the person the conversation is with, by name', async () => {
    fetchTemplate.mockResolvedValue(template());

    draw();

    await waitFor(() =>
      expect(screen.getByText('tasklib.preview.assignTo Ana Moldovan')).toBeDefined(),
    );
    fireEvent.click(screen.getByText('tasklib.preview.assignTo Ana Moldovan'));

    expect(stamp).toHaveBeenCalledWith(
      { id: 'tpl-1', task: { assigneeId: 'ana' } },
      expect.anything(),
    );
  });

  it('offers nothing to assign in a room with no one counterpart', async () => {
    fetchTemplate.mockResolvedValue(template());

    draw(true, null);

    await waitFor(() => expect(screen.getByText('Write the summary')).toBeDefined());
    expect(screen.queryByText(/tasklib\.preview\.assignTo/)).toBeNull();
  });

  it('will not assign from a template nobody has approved', async () => {
    fetchTemplate.mockResolvedValue(template({ status: 'DRAFT' }));

    draw();

    await waitFor(() => expect(screen.getByText('Write the summary')).toBeDefined());
    expect(screen.queryByText(/tasklib\.preview\.assignTo/)).toBeNull();
  });
});
