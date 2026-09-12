// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { NewTemplateStep } from '../api/processApi';

import { AuthorTemplateDialog } from './AuthorTemplateDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown> | string) =>
      options === undefined || typeof options === 'string'
        ? key
        : `${key}:${JSON.stringify(options)}`,
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const authorMutate = vi.fn();
let authorState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../hooks/useProcesses', () => ({
  useAuthorTemplate: () => ({ ...authorState, mutate: authorMutate, reset: vi.fn() }),
}));

vi.mock('./TaskTemplatePicker', () => ({
  TaskTemplatePicker: ({
    id,
    value,
    invalid,
    onChange,
  }: {
    id?: string;
    value: string;
    invalid?: boolean;
    onChange: (chosen: string) => void;
  }) => (
    <select
      id={id}
      value={value}

      aria-invalid={invalid === true ? true : undefined}
      onChange={(event) => {
        onChange(event.target.value);
      }}
    >
      <option value="">—</option>
      <option value="tt-1">Pregătește echipamentul</option>
      <option value="tt-2">Prima zi</option>
    </select>
  ),
}));

function draw() {
  render(<AuthorTemplateDialog open onClose={() => undefined} onAuthored={() => undefined} />);
}

function type(label: string, value: string): void {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function sent(): { name: string; overview: string | null; steps: NewTemplateStep[] } {
  const [payload] = authorMutate.mock.calls[0] as [
    { name: string; overview: string | null; steps: NewTemplateStep[] },
  ];
  return payload;
}

beforeEach(() => {
  authorState = { isPending: false, error: undefined };
  authorMutate.mockReset();
});

afterEach(cleanup);

describe('the authoring dialog', () => {
  it('sends the steps and no dependencies at all', () => {
    draw();
    type('process.author.name', 'Integrare angajat nou');
    type('process.author.stepTitle:{"position":1}', 'tt-1');

    fireEvent.click(screen.getByText('process.author.confirm'));

    expect(sent().name).toBe('Integrare angajat nou');
    expect(sent().steps).toHaveLength(1);
    expect(sent()).not.toHaveProperty('dependencies');
  });

  it('sends null for what was left blank, never an empty string', () => {
    draw();
    type('process.author.name', 'Integrare angajat nou');
    type('process.author.stepTitle:{"position":1}', 'tt-1');

    fireEvent.click(screen.getByText('process.author.confirm'));

    expect(sent().overview).toBeNull();
    expect(sent().steps[0]?.expectedDurationHours).toBeNull();
  });

  it('adds a step when asked, and sends both', () => {
    draw();
    type('process.author.name', 'Integrare angajat nou');
    type('process.author.stepTitle:{"position":1}', 'tt-1');
    fireEvent.click(screen.getByText('process.author.addStep'));
    type('process.author.stepTitle:{"position":2}', 'tt-2');

    fireEvent.click(screen.getByText('process.author.confirm'));

    expect(sent().steps.map((step) => step.taskTemplateId)).toEqual(['tt-1', 'tt-2']);
  });

  it('offers no way to remove the only step', () => {
    draw();

    expect(screen.queryByText('process.edit.removeStep')).toBeNull();

    fireEvent.click(screen.getByText('process.author.addStep'));

    expect(screen.queryAllByText('process.edit.removeStep')).toHaveLength(2);
  });

  it('marks the step the server named, by its position', () => {
    authorState = {
      isPending: false,
      error: new ApiError(400, {
        code: 'STEP_TASK_TEMPLATE_REQUIRED',
        message: 'Step 2 names no task template.',
        details: [{ field: 'steps[1].taskTemplateId', rule: 'REQUIRED' }],
      }),
    };
    draw();
    fireEvent.click(screen.getByText('process.author.addStep'));

    const second = screen.getByLabelText('process.author.stepTitle:{"position":2}');
    const first = screen.getByLabelText('process.author.stepTitle:{"position":1}');
    expect(second.getAttribute('aria-invalid')).toBe('true');
    expect(first.getAttribute('aria-invalid')).not.toBe('true');
  });

  it('names the refusal it was given rather than reporting that something went wrong', () => {
    authorState = {
      isPending: false,
      error: new ApiError(409, { code: 'TEMPLATE_NAME_TAKEN', message: 'already exists' }),
    };

    draw();

    expect(screen.queryByText('process.author.error.TEMPLATE_NAME_TAKEN')).not.toBeNull();
    expect(screen.queryByText('process.author.error.UNKNOWN')).toBeNull();
  });

  it('will not submit without a name and at least one titled step', () => {
    draw();
    type('process.author.name', 'Integrare angajat nou');

    fireEvent.click(screen.getByText('process.author.confirm'));

    expect(authorMutate).not.toHaveBeenCalled();
  });

  it('guards against a second submission while the first is in flight', () => {
    authorState = { isPending: true, error: undefined };

    draw();

    expect(screen.queryByText('process.author.submitting')).not.toBeNull();
  });

  it('adds a row without closing the dialog', () => {
    const onClose = vi.fn();
    render(<AuthorTemplateDialog open onClose={onClose} onAuthored={() => undefined} />);

    expect(screen.getAllByLabelText(/process\.author\.stepTitle/)).toHaveLength(1);

    fireEvent.click(screen.getByText('process.author.addStep'));

    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getAllByLabelText(/process\.author\.stepTitle/)).toHaveLength(2);
  });
});
