import type { JSX } from 'react';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
  labels: {
    previous: string;
    next: string;

    status: string;
  };
}

export function Pagination({
  page,
  totalPages,
  onPage,
  labels,
}: PaginationProps): JSX.Element | null {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav className="fo-pager" aria-label={labels.status}>
      <button
        type="button"
        className="ui-button ui-button-quiet"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
      >
        {labels.previous}
      </button>
      <span className="fo-pager-status" aria-live="polite">
        {labels.status}
      </span>
      <button
        type="button"
        className="ui-button ui-button-quiet"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
      >
        {labels.next}
      </button>
    </nav>
  );
}
