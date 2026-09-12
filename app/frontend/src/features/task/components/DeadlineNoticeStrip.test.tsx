// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { DeadlineNoticeStrip } from './DeadlineNoticeStrip';

const acknowledgeMutate = vi.fn();
const onChange = vi.fn();
let noticesState: { data?: { notices: unknown[] } } = { data: { notices: [] } };

vi.mock('../hooks/useTasks', () => ({
  useDeadlineNotices: () => noticesState,
  useAcknowledgeDeadlineNotice: () => ({ mutate: acknowledgeMutate, isPending: false }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

function notice(overrides: Record<string, unknown> = {}) {
  return {
    taskId: 'task-1',
    taskTitle: 'Pregătește dosarul fiscal',
    assigneeId: 'andrei',
    assigneeName: 'Andrei Munteanu',
    deadline: '2026-09-15T15:00:00Z',
    setAt: '2026-08-12T09:00:00Z',
    ...overrides,
  };
}

afterEach(() => {
  cleanup();
  acknowledgeMutate.mockReset();
  onChange.mockReset();
  noticesState = { data: { notices: [] } };
});

describe('the deadline notice region', () => {
  it('renders nothing at all when there is nothing to say', () => {
    const { container } = render(<DeadlineNoticeStrip onChange={onChange} />);

    expect(container.firstChild).toBeNull();
  });

  it('names who chose the date, on which work, and when it falls', () => {
    noticesState = { data: { notices: [notice()] } };

    render(<DeadlineNoticeStrip onChange={onChange} />);

    const row = screen.getByText(/Andrei Munteanu/);
    expect(row.textContent).toContain('Pregătește dosarul fiscal');
  });

  it('shows a notice from somebody erased as a former member rather than hiding it', () => {
    noticesState = { data: { notices: [notice({ assigneeName: '' })] } };

    render(<DeadlineNoticeStrip onChange={onChange} />);

    expect(screen.getByText(/task\.formerMember/)).toBeTruthy();
  });

  it('acknowledges the task the row is about, and not merely something', () => {
    noticesState = { data: { notices: [notice({ taskId: 'task-7' })] } };

    render(<DeadlineNoticeStrip onChange={onChange} />);
    fireEvent.click(screen.getByText('task.setDeadline.acknowledge'));

    expect(acknowledgeMutate).toHaveBeenCalledWith('task-7');
  });

  it('hands the right task to whoever wants to change the date', () => {
    noticesState = { data: { notices: [notice({ taskId: 'task-9' })] } };

    render(<DeadlineNoticeStrip onChange={onChange} />);
    fireEvent.click(screen.getByText('task.setDeadline.change'));

    expect(onChange).toHaveBeenCalledWith('task-9');
  });

  it('gives every notice its own controls', () => {
    noticesState = {
      data: {
        notices: [notice({ taskId: 'a' }), notice({ taskId: 'b', assigneeName: 'Elena Dobre' })],
      },
    };

    render(<DeadlineNoticeStrip onChange={onChange} />);

    expect(screen.getAllByText('task.setDeadline.acknowledge')).toHaveLength(2);
    expect(screen.getAllByText('task.setDeadline.change')).toHaveLength(2);
  });
});
