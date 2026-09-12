// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { NoticeCentre } from './NoticeCentre';
import { NoticeProvider } from './NoticeProvider';
import { useAnnounce } from './useNotices';
import type { Announcement } from './Notice';

function Announcer({ announcement }: { announcement: Announcement }) {
  const announce = useAnnounce();
  return (
    <button type="button" onClick={() => announce(announcement)}>
      say it
    </button>
  );
}

function draw(announcement: Announcement) {
  return render(
    <NoticeProvider>
      <Announcer announcement={announcement} />
      <NoticeCentre label="notices" dismissLabel="dismiss" />
    </NoticeProvider>,
  );
}

function say(): void {
  fireEvent.click(screen.getByRole('button', { name: 'say it' }));
}

function standing(): HTMLElement[] {
  return screen.queryAllByRole('listitem');
}

function theOne(): HTMLElement {
  const [only] = standing();
  if (only === undefined) {
    throw new Error('no notice is standing');
  }
  return only;
}

afterEach(() => {
  cleanup();
});

describe('a notice shows how long it has left', () => {
  it('draws a countdown for as long as the dwell lasts', () => {
    const { container } = draw({ tone: 'done', message: 'Assigned to Mihai Barbu' });
    say();

    const life = container.querySelector<HTMLElement>('.ui-notice-life');

    expect(life).not.toBeNull();
    expect(life?.style.animationDuration).toBe('6000ms');
  });

  it('draws no countdown on a failure, which has nothing to count down to', () => {
    const { container } = draw({ tone: 'failed', message: 'The run stopped at Observe' });
    say();

    expect(container.querySelector('.ui-notice-life')).toBeNull();
  });
});

describe('reaching for a notice does not lose it', () => {
  it('marks itself held while the pointer is over it', () => {
    draw({ tone: 'done', message: 'Assigned to Mihai Barbu' });
    say();

    fireEvent.mouseEnter(theOne());

    expect(theOne().getAttribute('data-held')).toBe('true');
  });

  it('lets go when the pointer leaves', () => {
    draw({ tone: 'done', message: 'Assigned to Mihai Barbu' });
    say();

    fireEvent.mouseEnter(theOne());
    fireEvent.mouseLeave(theOne());

    expect(theOne().getAttribute('data-held')).toBe('false');
  });

  it('holds while something inside it has focus, so a keyboard reach is not punished', () => {
    draw({ tone: 'done', message: 'Assigned to Mihai Barbu' });
    say();

    fireEvent.focus(screen.getByRole('button', { name: 'dismiss' }));

    expect(theOne().getAttribute('data-held')).toBe('true');
  });
});
