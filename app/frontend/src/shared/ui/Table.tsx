import type { JSX, ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  cell: (row: T) => ReactNode;

  numeric?: boolean;

  width?: string;

  headerHidden?: boolean;
}

interface TableProps<T> {
  caption: string;
  columns: ReadonlyArray<Column<T>>;
  rows: readonly T[];
  rowKey: (row: T) => string;

  empty?: ReactNode;
}

export function Table<T>({ caption, columns, rows, rowKey, empty }: TableProps<T>): JSX.Element {
  if (rows.length === 0 && empty !== undefined) {
    return <>{empty}</>;
  }

  const sized = columns.some((column) => column.width !== undefined);

  return (
    <div className="ui-table-scroll">
      <table
        className={sized ? 'ui-table ui-table--sized' : 'ui-table'}
        style={sized ? { tableLayout: 'fixed' } : undefined}
      >
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                style={{ ...alignment(column), width: column.width }}
              >
                {column.headerHidden === true ? (
                  <span className="sr-only">{column.header}</span>
                ) : (
                  column.header
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) => (
                <td key={column.key} style={alignment(column)}>
                  {column.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function alignment<T>(column: Column<T>): { textAlign: 'end' } | undefined {
  return column.numeric === true ? { textAlign: 'end' } : undefined;
}
