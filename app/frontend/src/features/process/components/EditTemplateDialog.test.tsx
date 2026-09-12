// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { EditTemplateDialog } from './EditTemplateDialog';
import type { Template } from '../api/processApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined || typeof options === 'string' ? key : `${key}`,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const save = { isPending: false, error: undefined, mutate: vi.fn(), reset: vi.fn() };

vi.mock('../hooks/useProcesses', () => ({
  useEditTemplate: () => save,
}));

vi.mock('./TaskTemplatePicker', () => ({
  TaskTemplatePicker: ({
    id,
    value,
    onChange,
  }: {
    id?: string;
    value: string;
    onChange: (chosen: string) => void;
  }) => (
    <select
      id={id}
      value={value}
      onChange={(event) => {
        onChange(event.target.value);
      }}
    >
      <option value="">—</option>
      <option value="tt-1">Pregătește echipamentul</option>
      <option value="tt-2">Prima zi cu echipa</option>
      <option value="tt-3">Verifică actele</option>
    </select>
  ),
}));

function template(overrides: Partial<Template> = {}): Template {
  return {
    id: 'template-1',
    name: 'Integrare angajat nou',
    overview: 'Cum primim pe cineva nou',
    active: true,
    authorId: 'maria',
    createdAt: '2026-08-01T08:00:00Z',
    steps: [
      {
        id: 'step-1',
        taskTemplateId: 'tt-1',
        title: 'Pregătește echipamentul',
        description: 'laptop și acces',
        expectedDurationHours: 4,
        position: 0,
      },
      {
        id: 'step-2',
        taskTemplateId: 'tt-2',
        title: 'Prima zi cu echipa',
        description: null,
        expectedDurationHours: null,
        position: 1,
      },
    ],
    dependencies: [],
    ...overrides,
  };
}

function open(which: Template = template()): void {
  render(<EditTemplateDialog open template={which} onClose={vi.fn()} onSaved={vi.fn()} />);
}

describe('EditTemplateDialog', () => {
  afterEach(cleanup);

  beforeEach(() => {
    save.mutate.mockClear();
  });

  it('opens with the template as it stands', () => {
    open();

    expect(screen.getByDisplayValue('Pregătește echipamentul')).toBeTruthy();
    expect(screen.getByDisplayValue('4')).toBeTruthy();
    expect(screen.getByDisplayValue('Cum primim pe cineva nou')).toBeTruthy();
  });

  it('sends each existing step with the identifier it already had', () => {
    open();

    fireEvent.click(screen.getByText('process.edit.confirm'));

    expect(save.mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        steps: [
          expect.objectContaining({ id: 'step-1', taskTemplateId: 'tt-1' }),
          expect.objectContaining({ id: 'step-2', taskTemplateId: 'tt-2' }),
        ],
      }),
      expect.anything(),
    );
  });

  it('sends a step added here with no identifier', () => {
    open();

    fireEvent.click(screen.getByText('process.author.addStep'));
    const pickers = screen.getAllByLabelText(/process.author.stepTitle/);
    fireEvent.change(pickers[pickers.length - 1] as HTMLElement, {
      target: { value: 'tt-3' },
    });
    fireEvent.click(screen.getByText('process.edit.confirm'));

    const sent = save.mutate.mock.calls[0]?.[0] as {
      steps: { id?: string; taskTemplateId: string }[];
    };
    expect(sent.steps).toHaveLength(3);
    expect(sent.steps[2]?.taskTemplateId).toBe('tt-3');
    expect(sent.steps[2]?.id).toBeUndefined();
  });

  it('sends the steps in the order they were moved into', () => {
    open();

    const ups = screen.getAllByLabelText(/process.edit.up/);
    fireEvent.click(ups[1] as HTMLElement);
    fireEvent.click(screen.getByText('process.edit.confirm'));

    expect(save.mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        steps: [
          expect.objectContaining({ id: 'step-2' }),
          expect.objectContaining({ id: 'step-1' }),
        ],
      }),
      expect.anything(),
    );
  });

  it('sends an absent duration rather than zero when the field is empty', () => {
    open();

    fireEvent.click(screen.getByText('process.edit.confirm'));

    const sent = save.mutate.mock.calls[0]?.[0] as {
      steps: { expectedDurationHours: number | null }[];
    };
    expect(sent.steps[1]?.expectedDurationHours).toBeNull();
    expect(sent.steps[0]?.expectedDurationHours).toBe(4);
  });

  it('does not offer to remove the only step', () => {
    open(
      template({
        steps: [
          {
            id: 'step-1',
            taskTemplateId: 'tt-1',
            title: 'Singurul pas',
            description: null,
            expectedDurationHours: null,
            position: 0,
          },
        ],
      }),
    );

    expect(screen.queryByText('process.edit.removeStep')).toBeNull();
  });

  it('cannot be saved while a step names no work', () => {
    open();

    const pickers = screen.getAllByLabelText(/process.author.stepTitle/);
    fireEvent.change(pickers[0] as HTMLElement, { target: { value: '' } });
    fireEvent.click(screen.getByText('process.edit.confirm'));

    expect(save.mutate).not.toHaveBeenCalled();
  });

  it('says that runs already under way are not affected', () => {
    open();

    expect(screen.getByText('process.edit.unaffected')).toBeTruthy();
    expect(screen.getByText('process.edit.removeTakesEdges')).toBeTruthy();
  });
});
