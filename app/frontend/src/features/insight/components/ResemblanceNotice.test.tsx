// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NoticeCentre } from '../../../shared/notice/NoticeCentre';
import { NoticeProvider } from '../../../shared/notice/NoticeProvider';
import type { ResemblanceAnswer } from '../api/resemblanceApi';
import { ResemblanceNotice } from './ResemblanceNotice';

const resembling = vi.fn<(text: string) => Promise<ResemblanceAnswer>>();

vi.mock('../api/resemblanceApi', () => ({
  resembling: (text: string) => resembling(text),
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

function draw(said: string | undefined, onPreview = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const drawn = render(
    <QueryClientProvider client={client}>
      <NoticeProvider>
        <ResemblanceNotice said={said} onPreview={onPreview} />
        <NoticeCentre label="notices" dismissLabel="insight.dismiss" />
      </NoticeProvider>
    </QueryClientProvider>,
  );
  return { ...drawn, onPreview };
}

function standing(): HTMLElement[] {
  return screen.queryAllByRole('listitem');
}

const MATCHED: ResemblanceAnswer = {
  match: {
    templateId: 'tpl-1',
    title: 'Monthly write-up — October Iulius',
    score: 0.929,
    why: 'matched_ad_hoc',
  },
};

describe('the notice about what somebody just said', () => {
  it('says nothing at all when nothing resembles the sentence', async () => {
    resembling.mockResolvedValue({ match: null });

    draw('Sounds good to me.');

    await waitFor(() => expect(resembling).toHaveBeenCalledWith('Sounds good to me.'));
    expect(standing()).toHaveLength(0);
  });

  it('asks nothing until something has been sent', () => {
    draw(undefined);

    expect(resembling).not.toHaveBeenCalled();
  });

  it('names the template a sentence resembles', async () => {
    resembling.mockResolvedValue(MATCHED);

    draw('Wrote the October Iulius monthly summary.');

    await waitFor(() =>
      expect(
        screen.getByText('chat.resemblance.looksLike Monthly write-up — October Iulius'),
      ).toBeDefined(),
    );
  });

  it('speaks through the one notice region rather than a second one of its own', async () => {
    resembling.mockResolvedValue(MATCHED);

    draw('Wrote the October Iulius monthly summary.');

    await waitFor(() => expect(standing()).toHaveLength(1));
    expect(screen.getAllByRole('status')).toHaveLength(1);
  });

  it('hands the template up to whoever composed it, rather than opening anything itself', async () => {
    resembling.mockResolvedValue(MATCHED);
    const { onPreview } = draw('Wrote the October Iulius monthly summary.');

    await waitFor(() => expect(screen.getByText('chat.resemblance.preview')).toBeDefined());
    fireEvent.click(screen.getByText('chat.resemblance.preview'));

    expect(onPreview).toHaveBeenCalledWith('tpl-1');
  });

  it('goes when it is dismissed', async () => {
    resembling.mockResolvedValue(MATCHED);

    draw('Wrote the October Iulius monthly summary.');

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'insight.dismiss' })).toBeDefined(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'insight.dismiss' }));

    await waitFor(() => expect(standing()).toHaveLength(0));
  });

  it('says nothing when the question could not be asked', async () => {
    resembling.mockRejectedValue(new Error('403'));

    draw('Wrote the October Iulius monthly summary.');

    await waitFor(() => expect(resembling).toHaveBeenCalled());
    expect(standing()).toHaveLength(0);
  });
});
