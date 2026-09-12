// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { DraftCandidate } from '../api/taskTemplateApi';
import { DraftCandidatesStrip } from './DraftCandidatesStrip';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: { count?: number }) =>
      vars?.count === undefined ? key : `${key}:${vars.count}`,
    i18n: { language: 'en' },
  }),
}));

vi.mock('../../../shared/ui/RelativeTime', () => ({
  RelativeTime: ({ value }: { value: string }) => <span>{value}</span>,
}));

function candidate(over: Partial<DraftCandidate> = {}): DraftCandidate {
  return {
    title: 'Pregătește raportul lunar',
    variants: ['Pregătește raportul lunar'],
    drafts: 1,
    firstSeen: '2026-08-01T09:00:00Z',
    lastSeen: '2026-08-20T09:00:00Z',
    templateIds: ['t-1'],
    ...over,
  };
}

afterEach(cleanup);

describe('the draft candidates strip', () => {
  it('renders nothing at all when there is nothing waiting', () => {
    const { container } = render(<DraftCandidatesStrip candidates={[]} />);

    expect(container.firstChild).toBeNull();
  });

  it('says how often each job was typed', () => {
    render(<DraftCandidatesStrip candidates={[candidate({ drafts: 9 })]} />);

    expect(screen.getByText('tasklib.candidates.typed:9')).toBeTruthy();
  });

  it('shows every wording when they differ', () => {
    render(
      <DraftCandidatesStrip
        candidates={[
          candidate({
            drafts: 3,
            variants: ['Verifică factura lunară', 'verifica factura lunara'],
          }),
        ]}
      />,
    );

    expect(screen.getByText(/Verifică factura lunară · verifica factura lunara/)).toBeTruthy();
  });

  it('says nothing about wordings when there is only one', () => {
    render(<DraftCandidatesStrip candidates={[candidate()]} />);

    expect(screen.queryByText(/tasklib.candidates.alsoTyped/)).toBeNull();
  });
});
