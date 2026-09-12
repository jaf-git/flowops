// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SuggestWorkPanel } from './SuggestWorkPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

let answer: unknown = undefined;
const asked: string[] = [];

vi.mock('../hooks/useWorkSuggestion', () => ({
  useWorkSuggestion: (conversationId: string) => {
    asked.push(conversationId);
    return { data: answer, mutate: () => undefined, isPending: false, isError: false };
  },
}));

vi.mock('../hooks/useCreateAgreedWork', () => ({
  useCreateAgreedWork: () => ({ mutate: () => undefined, isPending: false, error: null }),
}));

afterEach(() => {
  answer = undefined;
  asked.length = 0;
  cleanup();
});

function draw(): void {
  render(<SuggestWorkPanel conversationId="c-1" people={[]} />);
}

describe('SuggestWorkPanel', () => {
  it('offers the question before anybody has asked it', () => {
    draw();

    expect(screen.getByRole('button', { name: 'chatAssist.ask' })).toBeTruthy();
    expect(screen.queryByText('chatAssist.nothingHere')).toBeNull();
  });

  it('says that it found nothing, and keeps the button, when the answer is not available', () => {
    answer = {
      available: false,
      shape: null,
      title: null,
      assigneeId: null,
      deadline: null,
      steps: [],
    };
    draw();

    expect(screen.getByText('chatAssist.nothingHere')).toBeTruthy();

    // The regression this guards: the whole panel used to unmount on `available: false`,
    // so pressing the button made the button disappear and said nothing at all.
    expect(screen.getByRole('button', { name: 'chatAssist.ask' })).toBeTruthy();
  });

  it('says the same when it is available but describes no steps', () => {
    answer = {
      available: true,
      shape: 'PROCESS',
      title: 'x',
      assigneeId: null,
      deadline: null,
      steps: [],
    };
    draw();

    expect(screen.getByText('chatAssist.nothingHere')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'chatAssist.useDraft' })).toBeNull();
  });

  it('offers the draft when there are steps to offer', () => {
    answer = {
      available: true,
      shape: 'PROCESS',
      title: 'Monthly report',
      assigneeId: null,
      deadline: null,
      steps: [{ title: { value: 'Write the draft' } }, { title: { value: 'Send it' } }],
    };
    draw();

    expect(screen.getByText('Write the draft')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'chatAssist.useDraft' })).toBeTruthy();
    expect(screen.queryByText('chatAssist.nothingHere')).toBeNull();
  });
});
