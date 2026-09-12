// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import { TaskMaterialPanel } from './TaskMaterialPanel';

const attachMutate = vi.fn();
const detachMutate = vi.fn();
const addMutate = vi.fn();
const tickMutate = vi.fn();
const removeMutate = vi.fn();

interface MaterialState {
  isPending?: boolean;
  isError?: boolean;
  data?: { links: unknown[]; checklist: unknown[] };
}

let materialState: MaterialState = {
  isPending: false,
  isError: false,
  data: { links: [], checklist: [] },
};
let attachError: unknown = undefined;

vi.mock('../hooks/useTasks', () => ({
  useTaskMaterial: () => materialState,
  useAttachLink: () => ({ mutate: attachMutate, isPending: false, error: attachError }),
  useDetachLink: () => ({ mutate: detachMutate, isPending: false, error: undefined }),
  useAddChecklistItem: () => ({ mutate: addMutate, isPending: false, error: undefined }),
  useTickChecklistItem: () => ({
    mutate: tickMutate,
    isPending: false,
    error: undefined,
    variables: undefined,
  }),
  useRemoveChecklistItem: () => ({ mutate: removeMutate, isPending: false, error: undefined }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) =>
      vars === undefined ? key : `${key}:${vars.done}/${vars.total}`,
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

function link(overrides: Record<string, unknown> = {}) {
  return {
    id: 'link-1',
    url: 'https://drive.atelier.ro/brief.pdf',
    label: 'Brief for Q3',
    displayText: 'Brief for Q3',
    role: 'INPUT',
    addedById: 'ionut',
    addedAt: '2026-08-13T09:00:00Z',
    ...overrides,
  };
}

function step(overrides: Record<string, unknown> = {}) {
  return {
    id: 'step-1',
    position: 0,
    text: 'Reconcile the ledger',
    done: false,
    doneAt: null,
    authoredById: 'andrei',
    ...overrides,
  };
}

afterEach(() => {
  cleanup();
  [attachMutate, detachMutate, addMutate, tickMutate, removeMutate].forEach((fn) => fn.mockReset());
  materialState = { isPending: false, isError: false, data: { links: [], checklist: [] } };
  attachError = undefined;
});

describe('the material panel', () => {
  it('shows no heading for a group with nothing in it', () => {
    materialState = { data: { links: [link({ role: 'INPUT' })], checklist: [] } };

    render(<TaskMaterialPanel taskId="task-1" mine />);

    const headings = screen.getAllByRole('heading').map((heading) => heading.textContent);
    expect(headings).toContain('task.material.role.INPUT');
    expect(headings).not.toContain('task.material.role.OUTPUT');
    expect(headings).not.toContain('task.material.role.REFERENCE');
  });

  it('shows no step heading until there is a step', () => {
    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(screen.queryAllByRole('heading')).toHaveLength(0);
  });

  it('opens links in a way the opened page cannot reach back through', () => {
    materialState = { data: { links: [link()], checklist: [] } };

    render(<TaskMaterialPanel taskId="task-1" mine />);

    const anchor = screen.getByText('Brief for Q3') as HTMLAnchorElement;
    expect(anchor.getAttribute('href')).toBe('https://drive.atelier.ro/brief.pdf');
    expect(anchor.getAttribute('rel')).toContain('noopener');
  });

  it('detaches the link the row is about, and not merely something', () => {
    materialState = { data: { links: [link({ id: 'link-7' })], checklist: [] } };

    render(<TaskMaterialPanel taskId="task-1" mine />);
    fireEvent.click(screen.getByText('task.material.detach'));

    expect(detachMutate).toHaveBeenCalledWith({ id: 'task-1', linkId: 'link-7' });
  });

  it('offers to attach rather than showing the form, until somebody asks', () => {
    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(document.getElementById('material-url')).toBeNull();
    fireEvent.click(screen.getByText('task.material.attach'));
    expect(document.getElementById('material-url')).not.toBeNull();
  });

  it('will not attach an empty address', () => {
    render(<TaskMaterialPanel taskId="task-1" mine />);
    fireEvent.click(screen.getByText('task.material.attach'));

    const attach = screen
      .getAllByText('task.material.attach')
      .map((node) => node.closest('button'))
      .find((button) => button?.disabled === true);
    expect(attach).toBeDefined();
  });

  it('sends the address, the label and the role it was given', () => {
    render(<TaskMaterialPanel taskId="task-1" mine />);
    fireEvent.click(screen.getByText('task.material.attach'));

    fireEvent.change(document.getElementById('material-url') as HTMLInputElement, {
      target: { value: 'https://drive.atelier.ro/result.xlsx' },
    });
    fireEvent.change(document.getElementById('material-label') as HTMLInputElement, {
      target: { value: 'The reconciliation' },
    });
    fireEvent.change(document.getElementById('material-role') as HTMLSelectElement, {
      target: { value: 'OUTPUT' },
    });

    const submit = screen
      .getAllByText('task.material.attach')
      .map((node) => node.closest('button') as HTMLButtonElement)
      .find((button) => !button.disabled) as HTMLButtonElement;
    fireEvent.click(submit);

    expect(attachMutate).toHaveBeenCalledTimes(1);
    const [payload] = attachMutate.mock.calls[0] as [Record<string, string>];
    expect(payload.url).toBe('https://drive.atelier.ro/result.xlsx');
    expect(payload.label).toBe('The reconciliation');
    expect(payload.role).toBe('OUTPUT');
  });

  it('counts what is done against what there is', () => {
    materialState = {
      data: {
        links: [],
        checklist: [
          step({ id: 'a', done: true, doneAt: '2026-08-13T10:00:00Z' }),
          step({ id: 'b', position: 1 }),
          step({ id: 'c', position: 2 }),
        ],
      },
    };

    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(screen.getByText('task.material.progress:1/3')).toBeTruthy();
  });

  it('ticks the step the box belongs to, in the direction it was moved', () => {
    materialState = { data: { links: [], checklist: [step({ id: 'step-9' })] } };

    render(<TaskMaterialPanel taskId="task-1" mine />);
    fireEvent.click(screen.getByRole('checkbox'));

    expect(tickMutate).toHaveBeenCalledWith({ id: 'task-1', itemId: 'step-9', done: true });
  });

  it('does not let the person who gave the work out tick a step', () => {
    materialState = { data: { links: [], checklist: [step()] } };

    render(<TaskMaterialPanel taskId="task-1" mine={false} />);

    expect((screen.getByRole('checkbox') as HTMLInputElement).disabled).toBe(true);
    expect(tickMutate).not.toHaveBeenCalled();
  });

  it('strikes a done step through rather than moving it', () => {
    materialState = {
      data: {
        links: [],
        checklist: [
          step({ id: 'a', text: 'First', done: true, doneAt: '2026-08-13T10:00:00Z' }),
          step({ id: 'b', text: 'Second', position: 1 }),
        ],
      },
    };

    render(<TaskMaterialPanel taskId="task-1" mine />);

    const labels = screen.getAllByText(/First|Second/);
    expect(labels).toHaveLength(2);
    const [first, second] = labels;
    expect(first?.textContent).toBe('First');

    expect(first?.className).toContain('ui-checkbox-label-struck');
    expect(second?.className).not.toContain('ui-checkbox-label-struck');
  });

  it('will not add a step with nothing written on it', () => {
    render(<TaskMaterialPanel taskId="task-1" mine />);
    fireEvent.click(screen.getByText('task.material.addStep'));

    const add = screen
      .getAllByText('task.material.addStep')
      .map((node) => node.closest('button'))
      .find((button) => button?.disabled === true);
    expect(add).toBeDefined();
  });

  it('says it is loading rather than showing an empty form', () => {
    materialState = { isPending: true, data: undefined };

    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(screen.getByText('task.material.loading')).toBeTruthy();
    expect(screen.queryByText('task.material.attach')).toBeNull();
  });

  it('says the material could not be loaded rather than showing it as empty', () => {
    materialState = { isPending: false, isError: true, data: undefined };

    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(screen.getByText('task.material.loadFailed')).toBeTruthy();
    expect(screen.queryByText('task.material.attach')).toBeNull();
  });

  it('says why an address was refused instead of failing silently', () => {
    attachError = new ApiError(422, {
      code: 'LINK_SCHEME_NOT_ALLOWED',
      message: 'Only web addresses beginning http:// or https:// can be attached.',
    });

    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(screen.getByText('task.material.error.scheme')).toBeTruthy();
  });

  it('still says something when the refusal is one it does not recognise', () => {
    attachError = new ApiError(500, { code: 'SOMETHING_NEW', message: 'unexpected' });

    render(<TaskMaterialPanel taskId="task-1" mine />);

    expect(screen.getByText('task.material.error.unknown')).toBeTruthy();
  });
});
