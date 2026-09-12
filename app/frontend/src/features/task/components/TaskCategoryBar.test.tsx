// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { TaskCategoryBar } from './TaskCategoryBar';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) =>
      values === undefined ? key : `${key}:${Object.values(values).join(',')}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const nameMutate = vi.fn();
const removeMutate = vi.fn();
let categoriesState: {
  data?: {
    categories: Array<{ id: string; name: string; taskCount: number }>;
    filings: Record<string, string>;
  };
} = { data: undefined };
let nameState: { isPending: boolean; isError: boolean } = { isPending: false, isError: false };

vi.mock('../hooks/useTaskCategories', () => ({
  useTaskCategories: () => categoriesState,
  useCreateTaskCategory: () => ({ ...nameState, mutate: nameMutate }),
  useDeleteTaskCategory: () => ({ isPending: false, mutate: removeMutate }),
}));

const AURORA = { id: 'cat-1', name: 'Aurora Coffee', taskCount: 2 };

beforeEach(() => {
  categoriesState = { data: { categories: [AURORA], filings: {} } };
  nameState = { isPending: false, isError: false };
  nameMutate.mockReset();
  removeMutate.mockReset();
});

afterEach(cleanup);

describe('the task grouping bar', () => {
  it('shows the count beside the grouping, which is a fact about work and not about anybody', () => {
    render(<TaskCategoryBar permissions={['TASK_VIEW_OWN']} />);

    expect(screen.getByText('Aurora Coffee · 2')).toBeTruthy();
  });

  it('offers no control at all to somebody without WORKSPACE_CONFIGURE', () => {
    render(<TaskCategoryBar permissions={['TASK_VIEW_OWN', 'TASK_EDIT']} />);

    expect(screen.getByText('Aurora Coffee · 2')).toBeTruthy();
    expect(screen.queryByLabelText('task.category.newLabel')).toBeNull();
    expect(screen.queryByText('task.category.add')).toBeNull();
  });

  it('names a grouping from what was typed, trimmed', () => {
    render(<TaskCategoryBar permissions={['WORKSPACE_CONFIGURE']} />);

    fireEvent.change(screen.getByLabelText('task.category.newLabel'), {
      target: { value: '  Q3 audit  ' },
    });
    fireEvent.click(screen.getByText('task.category.add'));

    expect(nameMutate).toHaveBeenCalledTimes(1);
    expect(nameMutate.mock.calls[0]?.[0]).toBe('Q3 audit');
  });

  it('cannot name a grouping with nothing but space in the box', () => {
    render(<TaskCategoryBar permissions={['WORKSPACE_CONFIGURE']} />);

    fireEvent.change(screen.getByLabelText('task.category.newLabel'), { target: { value: '   ' } });

    expect(screen.getByText('task.category.add').hasAttribute('disabled')).toBe(true);
  });

  it('says so when the name is already taken', () => {
    nameState = { isPending: false, isError: true };
    render(<TaskCategoryBar permissions={['WORKSPACE_CONFIGURE']} />);

    expect(screen.getByRole('alert').textContent).toBe('task.category.taken');
  });

  it('renders nothing when there are no groupings and the viewer cannot make one', () => {
    categoriesState = { data: { categories: [], filings: {} } };
    const { container } = render(<TaskCategoryBar permissions={['TASK_VIEW_OWN']} />);

    expect(container.firstChild).toBeNull();
  });

  it('still offers the box when there are no groupings yet and the viewer may make one', () => {
    categoriesState = { data: { categories: [], filings: {} } };
    render(<TaskCategoryBar permissions={['WORKSPACE_CONFIGURE']} />);

    expect(screen.getByText('task.category.none')).toBeTruthy();
    expect(screen.getByLabelText('task.category.newLabel')).toBeTruthy();
  });
});
