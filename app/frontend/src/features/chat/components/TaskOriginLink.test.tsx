// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { TaskOriginLink } from './TaskOriginLink';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}));

let origin: {
  data: { conversationId: string; messageId: string } | undefined;
  isSuccess: boolean;
} = { data: undefined, isSuccess: false };

vi.mock('../hooks/useChat', () => ({
  useTaskOrigin: () => origin,
}));

afterEach(() => {
  cleanup();
  origin = { data: undefined, isSuccess: false };
});

describe('the way back from a task to what was said', () => {
  it('offers the participant the message it came from', () => {
    origin = { data: { conversationId: 'c1', messageId: 'm1' }, isSuccess: true };
    const onOpen = vi.fn();

    render(<TaskOriginLink taskId="t1" onOpen={onOpen} />);
    fireEvent.click(screen.getByRole('button', { name: 'chat.origin.open' }));

    expect(onOpen).toHaveBeenCalledWith('c1');
  });

  it('renders nothing at all when the server will not say', () => {
    origin = { data: undefined, isSuccess: false };

    const { container } = render(<TaskOriginLink taskId="t1" onOpen={vi.fn()} />);

    expect(container.childElementCount).toBe(0);
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('renders nothing while the answer is still unknown', () => {
    origin = { data: undefined, isSuccess: false };

    const { container } = render(<TaskOriginLink taskId="t1" onOpen={vi.fn()} />);

    expect(container.textContent).toBe('');
  });
});
