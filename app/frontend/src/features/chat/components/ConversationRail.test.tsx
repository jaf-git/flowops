// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ConversationRow } from '../api/chatApi';
import { ConversationRail } from './ConversationRail';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function row(over: Partial<ConversationRow> = {}): ConversationRow {
  return {
    id: 'c1',
    kind: 'DIRECT',
    counterpartId: 'p1',
    counterpartName: 'Ana Ionescu',
    counterpartActive: true,
    name: null,
    lastMessagePreview: 'Sunăm furnizorul',
    lastMessageDeleted: false,
    lastMessageAt: '2026-08-18T09:00:00Z',
    unreadCount: 0,
    ...over,
  };
}

describe('the conversation rail', () => {
  it('offers an invitation when there is nothing yet, rather than an empty void', () => {
    render(
      <ConversationRail
        conversations={[]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(screen.getByText('chat.rail.empty')).toBeDefined();
  });

  it('shows skeleton rows while loading rather than a spinner over a blank pane', () => {
    const { container } = render(
      <ConversationRail conversations={[]} selected={undefined} onSelect={() => {}} loading />,
    );

    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(screen.queryByText('chat.rail.empty')).toBeNull();
  });

  it('groups by kind, with Announcements pinned above channels, groups and direct messages', () => {
    render(
      <ConversationRail
        conversations={[
          row({ id: 'd', kind: 'DIRECT' }),
          row({ id: 'a', kind: 'ANNOUNCEMENT', counterpartName: null }),
          row({ id: 'g', kind: 'GROUP', counterpartName: null, name: 'Aurora launch' }),
          row({ id: 'c', kind: 'CHANNEL', counterpartName: null, name: 'CONTENT' }),
        ]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    const headings = screen.getAllByRole('heading').map((heading) => heading.textContent);
    expect(headings).toEqual([
      'chat.rail.group.announcements',
      'chat.rail.group.channels',
      'chat.rail.group.groups',
      'chat.rail.group.direct',
    ]);
  });

  it('names a channel and a group by their own name rather than by a missing counterpart', () => {
    render(
      <ConversationRail
        conversations={[
          row({ id: 'g', kind: 'GROUP', counterpartName: null, name: 'Aurora launch' }),
          row({ id: 'c', kind: 'CHANNEL', counterpartName: null, name: 'CONTENT' }),
        ]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(screen.getByText('Aurora launch')).toBeDefined();
    expect(screen.getByText('CONTENT')).toBeDefined();
    expect(screen.queryByText('chat.rail.formerMember')).toBeNull();
  });

  it('offers rooms the caller is not in, carrying a name and no reading of them', () => {
    const joined = vi.fn();

    render(
      <ConversationRail
        conversations={[]}
        joinable={[{ id: 'r1', name: 'Aurora launch' }]}
        onJoin={joined}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(screen.getByText('chat.rail.group.joinable')).toBeDefined();
    expect(screen.getByText('Aurora launch')).toBeDefined();

    expect(screen.queryByText('chat.rail.noMessagesYet')).toBeNull();

    screen.getByText('chat.rail.join').click();
    expect(joined).toHaveBeenCalledWith('r1');
  });

  it('says a withdrawn last message was deleted rather than showing it or going blank', () => {
    render(
      <ConversationRail
        conversations={[
          row({ lastMessageDeleted: true, lastMessagePreview: 'nu ar trebui să apară' }),
        ]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(screen.getByText('chat.message.deleted')).toBeDefined();
    expect(screen.queryByText('nu ar trebui să apară')).toBeNull();
  });

  it('renders an erased counterpart as a former member and never as a blank', () => {
    render(
      <ConversationRail
        conversations={[row({ counterpartName: null })]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(screen.getByText('chat.rail.formerMember')).toBeDefined();
  });

  it('marks a deactivated counterpart in words, since there is nobody to reach', () => {
    render(
      <ConversationRail
        conversations={[row({ counterpartActive: false })]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(screen.getByText('chat.rail.deactivated')).toBeDefined();
  });

  it('shows an unread badge only when something is unread', () => {
    const { rerender } = render(
      <ConversationRail
        conversations={[row({ unreadCount: 0 })]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );
    expect(screen.queryByText('3')).toBeNull();

    rerender(
      <ConversationRail
        conversations={[row({ unreadCount: 3 })]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );
    expect(screen.getByText('3')).toBeDefined();
  });

  it('renders no number about a person except the caller"s own unread count', () => {
    const { container } = render(
      <ConversationRail
        conversations={[row({ unreadCount: 2 })]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    const numbers = (container.textContent ?? '').match(/\d+/g) ?? [];
    expect(numbers).toEqual(['2']);
  });
});
