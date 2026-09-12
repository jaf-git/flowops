// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { ReportingTree, type ReportingTreeNode } from './ReportingTree';

afterEach(cleanup);

const TREE: ReportingTreeNode[] = [
  {
    id: 'maria',
    content: <span>Maria Ionescu</span>,
    children: [
      {
        id: 'ionut',
        content: <span>Ionuț Petrescu</span>,
        children: [{ id: 'ioana', content: <span>Ioana Radu</span>, children: [] }],
      },
    ],
  },
];

const TREE_WITH_A_ROW_CONTROL: ReportingTreeNode[] = [
  {
    id: 'maria',
    content: <span>Maria Ionescu</span>,
    children: [
      {
        id: 'elena',
        content: (
          <>
            <span>elena@atelier.ro</span>
            <button type="button">Retrage invitația</button>
          </>
        ),
        children: [],
      },
    ],
  },
];

function items(): HTMLElement[] {
  return screen.getAllByRole('treeitem');
}

describe('the reporting tree', () => {
  it('is a tree to assistive technology, with the depth on every row', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE} />);

    expect(screen.getByRole('tree')).toBeTruthy();
    expect(items()).toHaveLength(3);
    expect(items()[0]?.getAttribute('aria-level')).toBe('1');
    expect(items()[1]?.getAttribute('aria-level')).toBe('2');
    expect(items()[2]?.getAttribute('aria-level')).toBe('3');
  });

  it('puts exactly one row in the tab order', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE} />);

    expect(items().filter((item) => item.getAttribute('tabindex') === '0')).toHaveLength(1);
    expect(items()[0]?.getAttribute('tabindex')).toBe('0');
  });

  it('moves focus down and up with the arrow keys', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE} />);
    const tree = screen.getByRole('tree');

    fireEvent.keyDown(tree, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(items()[1]);

    fireEvent.keyDown(tree, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(items()[2]);

    fireEvent.keyDown(tree, { key: 'ArrowUp' });
    expect(document.activeElement).toBe(items()[1]);
  });

  it('goes to the first and last rows with Home and End', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE} />);
    const tree = screen.getByRole('tree');

    fireEvent.keyDown(tree, { key: 'End' });
    expect(document.activeElement).toBe(items()[2]);

    fireEvent.keyDown(tree, { key: 'Home' });
    expect(document.activeElement).toBe(items()[0]);
  });

  it('stops at the ends rather than wrapping around', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE} />);
    const tree = screen.getByRole('tree');

    fireEvent.keyDown(tree, { key: 'ArrowUp' });
    expect(document.activeElement).toBe(items()[0]);

    fireEvent.keyDown(tree, { key: 'End' });
    fireEvent.keyDown(tree, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(items()[2]);
  });

  it('leaves an arrow key to a control inside a row rather than claiming it for the tree', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE_WITH_A_ROW_CONTROL} />);
    const withdraw = screen.getByRole('button', { name: 'Retrage invitația' });
    withdraw.focus();

    const buttonStillSeesTheKey = fireEvent.keyDown(withdraw, { key: 'ArrowDown' });

    expect(document.activeElement).toBe(withdraw);
    expect(buttonStillSeesTheKey).toBe(true);
  });

  it('carries the focus ring as a class, because focus-visible cannot be expressed inline', () => {
    render(<ReportingTree label="Who reports to whom" nodes={TREE} />);

    expect(items()[0]?.className).toContain('ui-tree-item');
  });
});
