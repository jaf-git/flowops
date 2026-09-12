// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../../shared/api/client';
import type { Template, TemplateSummary } from '../api/processApi';

import { TemplateScreen } from './TemplateScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined || typeof options === 'string'
        ? key
        : `${key}:${JSON.stringify(options)}`,
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const templatesState: {
  isPending: boolean;
  isError: boolean;
  data?: { templates: TemplateSummary[] };
} = { isPending: false, isError: false, data: { templates: [] } };
const templateState: { isError: boolean; data?: Template } = { isError: false, data: undefined };
let drawState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };
let eraseState: { isPending: boolean; error: unknown } = { isPending: false, error: undefined };

vi.mock('../components/TaskTemplatePicker', () => ({
  TaskTemplatePicker: ({ id, value }: { id?: string; value: string }) => (
    <select id={id} value={value} onChange={() => undefined}>
      <option value={value}>{value}</option>
    </select>
  ),
}));

vi.mock('../hooks/useProcesses', () => ({
  useTemplates: () => templatesState,
  useTemplate: () => templateState,
  useDrawDependency: () => ({ ...drawState, mutate: vi.fn(), reset: vi.fn() }),

  useRetireTemplate: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),
  useEraseDependency: () => ({ ...eraseState, mutate: vi.fn(), reset: vi.fn() }),
  useAuthorTemplate: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),
  useStartInstance: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),

  useEditTemplate: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),
}));

function template(): Template {
  return {
    id: 'template-1',
    name: 'Integrare angajat nou',
    overview: 'Cum integrăm un coleg nou',
    active: true,
    authorId: 'maria',
    createdAt: '2026-08-12T09:00:00Z',
    steps: [
      {
        id: 'a',
        taskTemplateId: 'tt-1',
        title: 'Pregătește echipamentul',
        description: null,
        expectedDurationHours: 4,
        position: 0,
      },
      {
        id: 'b',
        taskTemplateId: 'tt-2',
        title: 'Prima zi',
        description: null,
        expectedDurationHours: 8,
        position: 1,
      },
    ],
    dependencies: [{ dependentStepId: 'b', dependsOnStepId: 'a' }],
  };
}

function reset(): void {
  templatesState.isPending = false;
  templatesState.isError = false;
  templatesState.data = {
    templates: [
      {
        id: 'template-1',
        name: 'Integrare angajat nou',
        overview: null,
        stepCount: 2,
        active: true,
      },
    ],
  };
  templateState.isError = false;
  templateState.data = template();
  drawState = { isPending: false, error: undefined };
  eraseState = { isPending: false, error: undefined };
}

beforeEach(reset);
afterEach(cleanup);

describe('the template screen', () => {
  it('invites somebody to record a process when there is none', () => {
    templatesState.data = { templates: [] };

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.empty.body')).not.toBeNull();
  });

  it('does not offer to record a process to somebody who may not', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_VIEW_OWN']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.section.templates')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'process.author.title' })).toBeNull();
  });

  it('offers to record a process to somebody who may', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByRole('button', { name: 'process.author.title' })).not.toBeNull();
  });

  it('marks the rows the server named when it refused a cycle', () => {
    drawState = {
      isPending: false,
      error: new ApiError(409, {
        code: 'GRAPH_CYCLE',
        message: 'those steps would wait for each other',
        details: [
          { field: 'a', rule: 'IN_CYCLE' },
          { field: 'b', rule: 'IN_CYCLE' },
        ],
      }),
    };

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.dependency.error.GRAPH_CYCLE')).not.toBeNull();
    expect(document.querySelectorAll('[data-in-cycle="true"]')).toHaveLength(2);
  });

  it('marks no rows when the refusal was not a cycle', () => {
    drawState = {
      isPending: false,
      error: new ApiError(422, { code: 'CROSS_TEMPLATE_EDGE', message: 'different processes' }),
    };

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.dependency.error.CROSS_TEMPLATE_EDGE')).not.toBeNull();
    expect(document.querySelectorAll('[data-in-cycle="true"]')).toHaveLength(0);
  });

  it('says that runs already under way are not affected by an edit', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR', 'PROCESS_TEMPLATE_EDIT']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    fireEvent.click(screen.getByText('process.edit.open'));

    expect(screen.queryByText('process.edit.unaffected')).not.toBeNull();
  });

  it('does not offer to change a template somebody else recorded', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_EDIT']}
        viewerId="ionut"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.edit.open')).toBeNull();
  });

  it('offers to change somebody else’s template to a person who sees every process', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_EDIT', 'PROCESS_VIEW_ANY']}
        viewerId="ionut"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.edit.open')).not.toBeNull();
  });

  it('offers to start a run to somebody who may instantiate', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_INSTANTIATE']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByRole('button', { name: 'process.start.title' })).not.toBeNull();
  });

  it('does not offer to start a run to somebody who may not', () => {
    render(
      <TemplateScreen
        permissions={['PROCESS_VIEW_OWN']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.section.templates')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'process.start.title' })).toBeNull();
  });

  it('says when the library could not be loaded', () => {
    templatesState.isError = true;

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.loadFailed')).not.toBeNull();
  });

  it('names a removal refusal by its code rather than as something unknown', () => {
    eraseState = {
      isPending: false,
      error: new ApiError(409, {
        code: 'GRAPH_STEP_STRANDED',
        message: 'a step nothing reaches',
      }),
    };

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.dependency.error.GRAPH_STEP_STRANDED')).not.toBeNull();
    expect(screen.queryByText('process.dependency.error.UNKNOWN')).toBeNull();
  });

  it('says something when the failure was not a refusal the server sent', () => {
    eraseState = { isPending: false, error: new TypeError('Failed to fetch') };

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.dependency.error.UNKNOWN')).not.toBeNull();
  });

  it('says when the open template could not be loaded', () => {
    templateState.isError = true;
    templateState.data = undefined;

    render(
      <TemplateScreen
        permissions={['PROCESS_TEMPLATE_AUTHOR']}
        viewerId="maria"
        steerers={[]}
        onStarted={() => undefined}
      />,
    );

    expect(screen.queryByText('process.loadFailed')).not.toBeNull();
  });
});
