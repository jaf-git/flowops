// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState, type JSX } from 'react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import type { ConversionDialogProps } from './ChatScreen';
import { ChatConversation, ChatRail } from './ChatScreen';

beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn();
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),

  initReactI18next: { type: '3rdParty', init: () => {} },
}));

const calls = { conversations: 0, messages: 0, markRead: 0, context: 0, convert: 0 };

vi.mock('../api/chatApi', () => ({
  fetchAssignmentContext: () =>
    Promise.resolve({
      counterpartId: 'p2',
      counterpartName: 'John Smith',
      counterpartActive: true,
      mayAssignTask: true,
      mayStartRun: true,
      templates: [],
    }),
  assignTaskInConversation: () => Promise.resolve({ taskId: 't9' }),
  startRunInConversation: () => Promise.resolve({ instanceId: 'i9' }),
  fetchConversations: () => {
    calls.conversations += 1;
    return Promise.resolve({
      cursor: 7,
      conversations: [
        {
          id: 'c1',
          kind: 'DIRECT',
          counterpartId: 'p2',
          counterpartName: 'John Smith',
          counterpartActive: true,
          lastMessagePreview: 'Sunăm furnizorul',
          lastMessageDeleted: false,
          lastMessageAt: '2026-08-18T09:00:00Z',
          unreadCount: 1,
        },
      ],
    });
  },
  fetchMessages: () => {
    calls.messages += 1;
    return Promise.resolve({
      cursor: 7,
      hasMore: false,
      messages: [
        {
          id: 'm1',
          authorId: 'p2',
          authorName: 'John Smith',
          body: 'Sunăm furnizorul',
          sentAt: '2026-08-18T09:00:00Z',
          editedAt: null,
          deletedAt: null,
          convertedTaskId: null,
          seq: 7,
        },
      ],
    });
  },
  markRead: () => {
    calls.markRead += 1;
    return Promise.resolve();
  },
  fetchConversionContext: () => {
    calls.context += 1;
    return Promise.resolve({
      suggestedAssigneeId: 'p2',
      suggestedAssigneeName: 'John Smith',
      suggestedAssigneeActive: true,
      title: 'Sunăm furnizorul',
      description: 'Sunăm furnizorul',
      instances: [{ id: 'run-1', name: 'Comandă mobilier' }],
    });
  },
  convertMessage: (
    _conversationId: string,
    _messageId: string,
    draft: { instanceId: string | null },
  ) => {
    calls.convert += 1;
    convertedWith = draft;
    return Promise.resolve({ taskId: 't1' });
  },
  sendMessage: () => Promise.resolve({}),
  startConversation: () => Promise.resolve({}),
  createGroup: () => Promise.resolve({}),
  joinRoom: () => Promise.resolve(),
  leaveRoom: () => Promise.resolve(),
  renameRoom: () => Promise.resolve(),
}));

let convertedWith: { instanceId: string | null } | undefined;

afterEach(() => {
  cleanup();
  convertedWith = undefined;
  for (const key of Object.keys(calls) as (keyof typeof calls)[]) {
    calls[key] = 0;
  }
});

function StubDialog({ open, prefill, processes, onConvert }: ConversionDialogProps): JSX.Element {
  const submit = (instanceId: string | null): void =>
    onConvert(
      {
        title: prefill.title,
        description: prefill.description,
        assigneeId: prefill.assigneeId ?? '',
        deadline: '2026-08-20T09:00:00Z',
        priority: 'NORMAL',
        instanceId,
      },
      { onSuccess: () => {}, onError: () => {} },
    );

  return !open ? (
    <span />
  ) : (
    <div>
      <span>dialog:{prefill.title}</span>
      <span>assignee:{prefill.assigneeId ?? 'none'}</span>

      {processes.map((run) => (
        <span key={run.id}>run:{run.name}</span>
      ))}
      <button type="button" onClick={() => submit(null)}>
        submit
      </button>
      <button type="button" onClick={() => submit(processes[0]?.id ?? null)}>
        submit into the run
      </button>
    </div>
  );
}

function railRowFor(name: string): HTMLElement {
  const row = screen
    .getAllByRole('button')
    .find((button) => (button.textContent ?? '').includes(name));
  if (row === undefined) {
    throw new Error(`no rail row for ${name}`);
  }
  return row;
}

function Harness(): JSX.Element {
  const [selected, setSelected] = useState<string | undefined>(undefined);
  return (
    <>
      <ChatRail
        people={[{ id: 'p2', displayName: 'John Smith' }]}

        viewerId="p1"
        selected={selected}
        onSelect={setSelected}
      />
      {selected === undefined ? null : (
        <ChatConversation
          conversationId={selected}
          onBack={() => setSelected(undefined)}
          ConversionDialog={StubDialog}
          onOpenTask={() => {}}
          onOpenRun={() => {}}
        />
      )}
    </>
  );
}

function renderChat(): void {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false, gcTime: 0 } },
  });
  render(
    <QueryClientProvider client={client}>
      <Harness />
    </QueryClientProvider>,
  );
}

describe('the chat screen', () => {
  it('lists conversations and opens one into the thread', async () => {
    renderChat();

    await waitFor(() => expect(railRowFor('John Smith')).toBeDefined());
    fireEvent.click(railRowFor('John Smith'));

    await waitFor(() => expect(screen.getByText('Sunăm furnizorul')).toBeDefined());
  });

  it('marks a thread read once, not once per refetch', async () => {
    renderChat();

    await waitFor(() => expect(railRowFor('John Smith')).toBeDefined());

    fireEvent.click(railRowFor('John Smith'));
    await waitFor(() => expect(calls.markRead).toBe(1));

    await new Promise((resolve) => setTimeout(resolve, 250));
    expect(calls.markRead).toBe(1);
    expect(calls.messages).toBeLessThanOrEqual(3);
  });

  it.skip('opens the dialog pre-filled from the message and converts through CHAT', async () => {
    renderChat();

    await waitFor(() => expect(railRowFor('John Smith')).toBeDefined());
    fireEvent.click(railRowFor('John Smith'));
    await waitFor(() => expect(screen.getByText('chat.message.makeTask')).toBeDefined());

    fireEvent.click(screen.getByText('chat.message.makeTask'));

    await waitFor(() => expect(screen.getByText('dialog:Sunăm furnizorul')).toBeDefined());
    expect(screen.getByText('assignee:p2')).toBeDefined();

    fireEvent.click(screen.getByText('submit'));
    await waitFor(() => expect(calls.convert).toBe(1));
  });

  it.skip('offers the runs the conversion context named, and converts into the chosen one', async () => {
    renderChat();
    await waitFor(() => expect(railRowFor('John Smith')).toBeDefined());
    fireEvent.click(railRowFor('John Smith'));
    await waitFor(() => expect(screen.getByText('chat.message.makeTask')).toBeDefined());
    fireEvent.click(screen.getByText('chat.message.makeTask'));

    await waitFor(() => expect(screen.getByText('run:Comandă mobilier')).toBeDefined());

    fireEvent.click(screen.getByText('submit into the run'));
    await waitFor(() => expect(calls.convert).toBe(1));
    expect(convertedWith?.instanceId).toBe('run-1');
  });

  it('fetches no thread and no conversion context until they are asked for', async () => {
    renderChat();
    await waitFor(() => expect(calls.conversations).toBeGreaterThan(0));

    expect(calls.messages).toBe(0);
    expect(calls.context).toBe(0);
  });
});
