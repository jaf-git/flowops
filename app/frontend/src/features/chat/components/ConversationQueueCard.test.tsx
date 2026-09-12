// @vitest-environment jsdom

import { readFileSync } from 'node:fs';
import { join } from 'node:path';

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

function card(): HTMLElement {
  const found = document.querySelector('.fo-queue-card');
  if (found === null) {
    throw new Error('no queue card rendered');
  }
  return found as HTMLElement;
}

describe('a queue card', () => {
  it('is a title row and an inner row, not a stack of lines', () => {
    render(
      <ConversationRail
        conversations={[row()]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    expect(card().querySelector('.fo-queue-head')).not.toBeNull();
    expect(card().querySelector('.fo-queue-row')).not.toBeNull();
    expect(card().querySelector('.fo-queue-glyph')).not.toBeNull();
    expect(card().querySelector('.fo-queue-open')?.textContent).toBe('↗');
  });

  it('puts the eyebrow above the title, which is the one place the reference is not followed', () => {
    render(
      <ConversationRail
        conversations={[row()]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    const slots = [...card().querySelectorAll('.fo-queue-eyebrow, .fo-queue-title')].map(
      (node) => node.className,
    );

    expect(slots).toEqual(['fo-queue-eyebrow', 'fo-queue-title']);
    expect(screen.getByText('chat.header.kind.DIRECT')).toBeDefined();
  });

  it('marks exactly one card as the selection, in the attribute the styling reads', () => {
    render(
      <ConversationRail
        conversations={[row({ id: 'a' }), row({ id: 'b' }), row({ id: 'c' })]}
        selected="b"
        onSelect={() => {}}
        loading={false}
      />,
    );

    const marked = document.querySelectorAll('.fo-queue-card[aria-current="true"]');

    expect(marked).toHaveLength(1);
    expect(document.querySelectorAll('.fo-queue-card')).toHaveLength(3);
  });

  it('says an inactive counterpart with a glyph and a word, never with a tint alone', () => {
    render(
      <ConversationRail
        conversations={[row({ counterpartActive: false })]}
        selected={undefined}
        onSelect={() => {}}
        loading={false}
      />,
    );

    const pill = card().querySelector('.fo-queue-pill');

    expect(pill?.textContent).toContain('—');
    expect(pill?.textContent).toContain('chat.rail.deactivated');
  });
});

describe('the queue card block in shell.css', () => {
  const BLOCK = /^\.fo-queue-list \{[\s\S]*$/m;

  it('carries no literal colour, so every value answers to a measured token', () => {
    const stylesheet = readFileSync(join(__dirname, '..', '..', '..', 'shell.css'), 'utf8');

    const block = BLOCK.exec(stylesheet)?.[0];
    expect(block, 'the queue card block is no longer in shell.css').toBeDefined();

    const declarations = (block ?? '').replace(/\/\*[\s\S]*?\*\//g, ' ');

    expect(
      [...declarations.matchAll(/#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/g)].map(
        (match) => match[0],
      ),
    ).toEqual([]);
  });
});
