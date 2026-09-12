// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { JSX } from 'react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import type { SpokenRow, WorkMarkRow } from '../api/chatApi';
import { MessageThread } from './MessageThread';

beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn();
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function message(over: Partial<SpokenRow> = {}): SpokenRow {
  return {
    kind: 'SPOKEN',
    id: 'm1',
    authorId: 'p1',
    authorName: 'Ana Ionescu',
    body: 'Sunăm furnizorul',
    sentAt: '2026-08-18T09:00:00Z',
    editedAt: null,
    deletedAt: null,
    convertedTaskId: null,
    seq: 1,
    ...over,
  };
}

describe('the message thread', () => {
  it('is calm when nothing has been said, rather than an error', () => {
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    expect(screen.getByText('chat.thread.empty')).toBeDefined();
  });

  it('opens the task from the chip', () => {
    const opened: string[] = [];
    render(
      <MessageThread
        onOpenTask={(id) => opened.push(id)}
        messages={[message({ convertedTaskId: 't1' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        onOpenRun={() => {}}
      />,
    );

    fireEvent.click(screen.getByText('chat.message.becameTask'));

    expect(opened).toEqual(['t1']);
  });

  it('replaces the action with a chip once the message has become a task', () => {
    render(
      <MessageThread
        onOpenTask={() => {}}
        messages={[message({ convertedTaskId: 't1' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        onOpenRun={() => {}}
      />,
    );

    expect(screen.getByText('chat.message.becameTask')).toBeDefined();
  });

  it('offers nothing on a deleted message, but keeps the chip if it had already become a task', () => {
    const { rerender } = render(
      <MessageThread
        onOpenTask={() => {}}
        messages={[message({ deletedAt: '2026-08-18T10:00:00Z' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        onOpenRun={() => {}}
      />,
    );

    rerender(
      <MessageThread
        onOpenTask={() => {}}
        messages={[message({ deletedAt: '2026-08-18T10:00:00Z', convertedTaskId: 't1' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        onOpenRun={() => {}}
      />,
    );
    expect(screen.getByText('chat.message.becameTask')).toBeDefined();
  });

  it('shows no receipt, no typing signal and no delivery state', () => {
    const { container } = render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[message()]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    const header = container.querySelector('article header');
    expect(header?.children).toHaveLength(2);

    expect(screen.getByText('Ana Ionescu')).toBeDefined();
    expect(screen.getByText('Sunăm furnizorul')).toBeDefined();
  });
});

describe('a work mark in the thread', () => {
  function mark(over: Partial<WorkMarkRow> = {}): WorkMarkRow {
    return {
      kind: 'WORK_MARK',
      id: 'w1',
      authorId: 'p1',
      authorName: 'Ionuț Petrescu',
      sentAt: '2026-08-21T09:14:00Z',
      seq: 4,
      work: { kind: 'TASK', id: 't1' },
      ...over,
    };
  }

  it('says what happened from a key rather than from stored words', () => {
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[mark()]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    expect(screen.getByText('chat.workMark.gaveTask')).toBeDefined();
  });

  it('tells a run apart from a task, because they open different screens', () => {
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[mark({ work: { kind: 'RUN', id: 'i1' } })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    expect(screen.getByText('chat.workMark.startedRun')).toBeDefined();
  });

  it('offers no convert action and no author bubble', () => {
    const { container } = render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[mark()]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );
    expect(container.querySelector('article')).toBeNull();
  });

  it('opens the work it names, through the caller', () => {
    const opened = vi.fn();
    const ran = vi.fn();
    render(
      <MessageThread
        onOpenTask={opened}
        onOpenRun={ran}
        messages={[mark()]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    fireEvent.click(screen.getByText('chat.workMark.open'));

    expect(opened).toHaveBeenCalledWith('t1');
    expect(ran).not.toHaveBeenCalled();
  });

  it('sends a run to the run screen rather than the task drawer', () => {
    const opened = vi.fn();
    const ran = vi.fn();
    render(
      <MessageThread
        onOpenTask={opened}
        onOpenRun={ran}
        messages={[mark({ work: { kind: 'RUN', id: 'i1' } })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    fireEvent.click(screen.getByText('chat.workMark.open'));

    expect(ran).toHaveBeenCalledWith('i1');
    expect(opened).not.toHaveBeenCalled();
  });

  it('renders a mark and a message together, in one thread', () => {
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[mark({ seq: 4 }), message({ seq: 3 })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    expect(screen.getByText('Sunăm furnizorul')).toBeDefined();
    expect(screen.getByText('chat.workMark.gaveTask')).toBeDefined();
  });
});

describe('the mark seam', () => {
  function workMark(over: Partial<WorkMarkRow> = {}): WorkMarkRow {
    return {
      kind: 'WORK_MARK',
      id: 'w1',
      authorId: 'p1',
      authorName: 'Ionuț Petrescu',
      sentAt: '2026-08-21T09:14:00Z',
      seq: 4,
      work: { kind: 'TASK', id: 't1' },
      ...over,
    };
  }

  it('puts the circle on a spoken message', () => {
    const markFor = vi.fn<(id: string, authorId: string) => JSX.Element>((id) => (
      <span>circle for {id}</span>
    ));
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[message({ id: 'm7' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        markFor={markFor}
      />,
    );

    expect(markFor).toHaveBeenCalledWith('m7', 'p1');
    expect(screen.getByText(/circle for m7/)).toBeDefined();
  });

  it('puts nothing on a work mark', () => {
    const markFor = vi.fn(() => <span>a circle</span>);
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[workMark()]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        markFor={markFor}
      />,
    );

    expect(markFor).not.toHaveBeenCalled();
    expect(screen.queryByText('a circle')).toBeNull();
  });

  it('explains a withdrawn message in place rather than offering a circle', () => {
    const markFor = vi.fn(() => <span>a circle</span>);
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[message({ deletedAt: '2026-08-21T10:00:00Z' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        markFor={markFor}
      />,
    );

    expect(markFor).not.toHaveBeenCalled();
    expect(screen.getByLabelText('chat.selection.cannot.WITHDRAWN')).toBeDefined();
    expect(screen.queryByRole('button', { name: /circle/ })).toBeNull();
  });

  it('brings the message it was given into view and marks it with a word, not only an edge', () => {
    vi.mocked(Element.prototype.scrollIntoView).mockClear();
    const used = vi.fn();

    const { container } = render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[message({ id: 'm2', body: 'newer' }), message({ id: 'm1', body: 'older' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
        focusMessageId="m1"
        onFocusUsed={used}
      />,
    );

    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ block: 'center' });
    expect(screen.getByText('chat.message.cameFor')).toBeDefined();
    expect(container.querySelectorAll('.fo-message--focused').length).toBe(1);

    expect(container.querySelector('.fo-message--focused')?.textContent?.includes('older')).toBe(
      true,
    );
    expect(used).toHaveBeenCalled();
  });

  it('marks nothing and centres nothing when no message was asked for', () => {
    vi.mocked(Element.prototype.scrollIntoView).mockClear();

    const { container } = render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[message()]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    expect(Element.prototype.scrollIntoView).not.toHaveBeenCalledWith({ block: 'center' });
    expect(screen.queryByText('chat.message.cameFor')).toBeNull();
    expect(container.querySelectorAll('.fo-message--focused').length).toBe(0);
  });

  it('renders nothing at all where no mark was supplied', () => {
    render(
      <MessageThread
        onOpenTask={() => {}}
        onOpenRun={() => {}}
        messages={[message({ deletedAt: '2026-08-21T10:00:00Z' })]}
        loading={false}
        onSend={() => {}}
        sending={false}
        failed={false}
      />,
    );

    expect(screen.queryByLabelText('chat.selection.cannot.WITHDRAWN')).toBeNull();
  });
});
