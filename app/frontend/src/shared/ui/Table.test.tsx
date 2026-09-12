// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { EmptyState } from './EmptyState';
import { Table, type Column } from './Table';

interface Person {
  id: string;
  name: string;
  reports: number;
}

const PEOPLE: Person[] = [
  { id: '1', name: 'Ioana Marinescu', reports: 4 },
  { id: '2', name: 'Andrei Popescu', reports: 0 },
];

const COLUMNS: Column<Person>[] = [
  { key: 'name', header: 'Name', cell: (person) => person.name },
  { key: 'reports', header: 'Reports', cell: (person) => person.reports, numeric: true },
];

afterEach(cleanup);

const CHOOSE_COLUMNS: ReadonlyArray<Column<Person>> = [
  { key: 'choose', header: 'Choose', headerHidden: true, cell: () => null },
  { key: 'name', header: 'Name', cell: (person: Person) => person.name },
];

describe('the table', () => {
  it('is named, so two tables on one screen are told apart', () => {
    render(<Table caption="People" columns={COLUMNS} rows={PEOPLE} rowKey={(p) => p.id} />);

    expect(screen.getByRole('table', { name: 'People' })).toBeDefined();
  });

  it('renders a row per item and a header per column', () => {
    render(<Table caption="People" columns={COLUMNS} rows={PEOPLE} rowKey={(p) => p.id} />);

    expect(screen.getAllByRole('columnheader')).toHaveLength(2);
    expect(screen.getAllByRole('row')).toHaveLength(3);
  });

  it('renders the empty state instead of a header over nothing', () => {
    render(
      <Table
        caption="People"
        columns={COLUMNS}
        rows={[]}
        rowKey={(p) => p.id}
        empty={<EmptyState heading="It's just you." body="Invite someone." />}
      />,
    );

    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.getByText("It's just you.")).toBeDefined();
  });

  it('gives a table that declares its widths a floor to lay them out in', () => {
    render(
      <Table
        caption="People"
        columns={COLUMNS.map((column, index) => ({
          ...column,
          width: index === 0 ? '70%' : '30%',
        }))}
        rows={PEOPLE}
        rowKey={(p) => p.id}
      />,
    );

    expect(screen.getByRole('table').className).toContain('ui-table--sized');
  });

  it('leaves an unsized table alone', () => {
    render(<Table caption="People" columns={COLUMNS} rows={PEOPLE} rowKey={(p) => p.id} />);

    expect(screen.getByRole('table').className).not.toContain('ui-table--sized');
  });

  it('hides a heading visually while keeping it for assistive technology', () => {
    render(<Table caption="People" columns={CHOOSE_COLUMNS} rows={PEOPLE} rowKey={(p) => p.id} />);

    const heading = screen.getByRole('columnheader', { name: 'Choose' });
    expect(heading.querySelector('.sr-only')).not.toBeNull();
  });

  it('aligns a numeric column to the end rather than to the right', () => {
    render(<Table caption="People" columns={COLUMNS} rows={PEOPLE} rowKey={(p) => p.id} />);

    expect(screen.getByRole('columnheader', { name: 'Reports' }).style.textAlign).toBe('end');
  });
});
