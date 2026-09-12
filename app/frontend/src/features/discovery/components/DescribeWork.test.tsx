// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { EnrichNodeRequest, NodeEnrichment } from '../api/discoveryApi';
import { DescribeWork } from './DescribeWork';

const enrichNode = vi.fn<(nodeId: string, fields: EnrichNodeRequest) => Promise<NodeEnrichment>>();

vi.mock('../api/discoveryApi', () => ({
  enrichNode: (nodeId: string, fields: EnrichNodeRequest) => enrichNode(nodeId, fields),
  answerNudge: vi.fn(),
  blockNode: vi.fn(),
  deleteNode: vi.fn(),
  endThread: vi.fn(),
  fetchJobsForConversation: vi.fn(),
  fetchNodeTrail: vi.fn(),
  fetchNudge: vi.fn(),
  markMessage: vi.fn(),
  openJob: vi.fn(),
  recordOutput: vi.fn(),
  relinkNode: vi.fn(),
  resumeNode: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}));

function enrichment(over: Partial<NodeEnrichment> = {}): NodeEnrichment {
  return {
    nodeId: 'node-1',
    title: 'caption set for Aurora',
    detail: null,
    checklist: null,
    accepted: true,
    correctable: true,
    ...over,
  };
}

function firstField(): HTMLElement {
  const fields = screen.getAllByRole('textbox');
  const field = fields[0];
  if (field === undefined) {
    throw new Error('the form offered no text field to type into');
  }
  return field;
}

function draw(title: string | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DescribeWork nodeId="node-1" title={title} />
    </QueryClientProvider>,
  );
}

describe('DescribeWork', () => {
  beforeEach(() => {
    enrichNode.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('says plainly that nobody has named the work yet', () => {
    draw(null);

    expect(screen.getByText('discovery.describe.unnamed')).toBeTruthy();
    expect(screen.getByText('discovery.describe.titleLabel')).toBeTruthy();
  });

  it('shows the name and withdraws the field once the work is named', () => {
    draw('caption set for Aurora');

    expect(screen.getByText('discovery.describe.named')).toBeTruthy();
    expect(screen.queryByText('discovery.describe.titleLabel')).toBeNull();
    expect(screen.queryByRole('button', { name: 'discovery.describe.correct' })).toBeNull();
  });

  it('will not save an empty answer', () => {
    draw(null);

    const save = screen.getByRole('button', { name: 'discovery.describe.save' });
    expect(save.hasAttribute('disabled')).toBe(true);
  });

  it('sends only the fields somebody actually filled in', async () => {
    enrichNode.mockResolvedValue(enrichment());
    draw(null);

    fireEvent.change(firstField(), {
      target: { value: 'caption set for Aurora' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'discovery.describe.save' }));

    await waitFor(() => {
      expect(enrichNode).toHaveBeenCalledWith('node-1', { title: 'caption set for Aurora' });
    });
  });

  it('reports an already-answered field as taken, not as an error', async () => {
    enrichNode.mockResolvedValue(enrichment({ title: 'the first answer', accepted: false }));
    draw(null);

    fireEvent.change(firstField(), {
      target: { value: 'something else entirely' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'discovery.describe.save' }));

    await waitFor(() => {
      expect(screen.getByText('discovery.describe.alreadyAnswered')).toBeTruthy();
    });
    expect(screen.getByText('discovery.describe.named')).toBeTruthy();
  });

  it('stops a title at the length the column holds', () => {
    draw(null);

    expect(firstField().getAttribute('maxlength')).toBe('120');
  });
});
