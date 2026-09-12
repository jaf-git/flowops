// @vitest-environment jsdom
import { cleanup, render as renderBare, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { JSX } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Instance, InstanceSummary } from '../api/processApi';

import { ProcessScreen } from './ProcessScreen';

function render(ui: JSX.Element) {
  return renderBare(<MemoryRouter>{ui}</MemoryRouter>);
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key}:${JSON.stringify(options)}`,

    i18n: { language: 'en' },
  }),

  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

const runsState: { isPending: boolean; isError: boolean; data?: { instances: InstanceSummary[] } } =
  { isPending: false, isError: false, data: { instances: [] } };
const runState: { isError: boolean; isPending: boolean; data?: Instance } = {
  isError: false,
  isPending: false,
  data: undefined,
};

vi.mock('./TemplateScreen', () => ({
  TemplateScreen: ({ onStarted }: { onStarted: (run: { id: string }) => void }) => (
    <button
      type="button"
      onClick={() => {
        onStarted({ id: 'run-9' });
      }}
    >
      stub: start a run
    </button>
  ),
}));

let askedForRun: string | null = null;

vi.mock('../hooks/useProcesses', () => ({
  useInstances: () => runsState,
  useInstance: (id: string | null) => {
    askedForRun = id;
    return runState;
  },
  useAssignStep: () => ({ isPending: false, error: undefined, mutate: vi.fn(), reset: vi.fn() }),
  useAssignablePeople: () => ({ isError: false, data: { people: [] } }),

  useReorderInstanceTasks: () => reorderMutation,
  useAddTaskToInstance: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),
  useRemoveTaskFromInstance: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),
  useAttachableTasks: () => ({ isPending: false, data: { tasks: [] } }),

  useTasksForANewProcess: () => ({ isPending: false, isError: false, data: { tasks: [] } }),
  useStartInstanceFromTasks: () => ({
    isPending: false,
    error: undefined,
    mutate: vi.fn(),
    reset: vi.fn(),
  }),
}));

const reorderMutation = { isPending: false, error: undefined, mutate: vi.fn(), reset: vi.fn() };

function summary(overrides: Partial<InstanceSummary> = {}): InstanceSummary {
  return {
    id: 'run-1',
    name: 'Integrare — Andrei',
    templateName: 'Integrare angajat nou',
    state: 'RUNNING',
    progress: { closed: 0, total: 3 },
    awaitingAssignmentCount: 1,
    ...overrides,
  };
}

function instance(overrides: Partial<Instance> = {}): Instance {
  return {
    id: 'run-1',
    name: 'Integrare — Andrei',
    state: 'RUNNING',
    templateId: 'template-1',
    templateName: 'Integrare angajat nou',
    processOwnerId: 'ioana',
    startedAt: '2026-08-12T09:00:00Z',
    completedAt: null,
    progress: { closed: 0, total: 2 },
    steps: [
      {
        id: 'step-1',
        definitionId: 'def-1',
        title: 'Pregătește echipamentul',
        description: null,
        expectedDurationHours: 4,
        position: 0,
        condition: 'REACHABLE',
        taskId: null,
        planned: true,
        optional: false,
        conditionNote: null,
        skipped: false,
        dependsOn: [],
        taskState: null,
        blockedReason: null,
        assigneeId: null,
        assigneeName: null,
        deadline: null,
        atRisk: false,
      },
      {
        id: 'step-2',
        definitionId: 'def-2',
        title: 'Prima zi',
        description: null,
        expectedDurationHours: 8,
        position: 1,
        condition: 'PENDING',
        taskId: null,
        planned: true,
        optional: false,
        conditionNote: null,
        skipped: false,
        dependsOn: ['step-1'],
        taskState: null,
        blockedReason: null,
        assigneeId: null,
        assigneeName: null,
        deadline: null,
        atRisk: false,
      },
    ],
    awaitingAssignment: ['step-1'],
    bottleneck: null,
    totalDurationMinutes: null,

    abandonedAt: null,
    abandonedReason: null,
    needingAttention: [],
    ...overrides,
  };
}

function reset(): void {
  askedForRun = null;
  runsState.isPending = false;
  runsState.isError = false;
  runsState.data = { instances: [summary()] };
  runState.isError = false;
  runState.isPending = false;
  runState.data = instance();
}

afterEach(() => {
  cleanup();
});

describe('the instance screen', () => {
  it('names who holds an assigned step, rather than saying somebody does', () => {
    reset();
    const [first, second] = instance().steps;
    if (first === undefined || second === undefined) {
      throw new Error('the default run must carry two steps');
    }
    runState.data = instance({
      steps: [
        {
          ...first,
          condition: 'ASSIGNED',
          taskId: 'task-1',
          taskState: 'CREATED',
          assigneeId: 'ana',
          assigneeName: 'Ana Moldovan',
          deadline: '2026-09-10T14:00:00Z',
        },
        second,
      ],
      awaitingAssignment: [],
    });

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByText(/Ana Moldovan/)).not.toBeNull();

    expect(screen.queryByText('process.condition.ASSIGNED')).toBeNull();
  });

  it('names the process a run is of, on its card and in its header', () => {
    reset();
    runsState.data = {
      instances: [
        { ...summary(), id: 'i1', name: 'Run 1', templateName: 'Integrare angajat nou' },
        { ...summary(), id: 'i2', name: 'Run 1', templateName: 'Plecare angajat' },
      ],
    };
    runState.data = instance({ name: 'Run 1', templateName: 'Integrare angajat nou' });

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryAllByText('Integrare angajat nou').length).toBeGreaterThan(0);
    expect(screen.queryByText('Plecare angajat')).not.toBeNull();
  });

  it('says nothing about the process of a run that was started from tasks', () => {
    reset();
    runsState.data = {
      instances: [{ ...summary(), id: 'i1', name: 'Ad-hoc', templateName: null }],
    };
    runState.data = instance({ name: 'Ad-hoc', templateName: null });

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.querySelectorAll('[data-testid="process-of-run"]')).toHaveLength(0);
  });

  it('says it is loading while the runs are still arriving', () => {
    reset();
    runsState.isPending = true;

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByText('process.loading')).not.toBeNull();
  });

  it('tells the person when the runs could not be loaded', () => {
    reset();
    runsState.isError = true;

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByText('process.loadFailed')).not.toBeNull();
  });

  it('invites somebody who may start a run to start one', () => {
    reset();
    runsState.data = { instances: [] };

    render(
      <ProcessScreen
        permissions={['PROCESS_VIEW_OWN', 'PROCESS_INSTANTIATE']}
        viewerId="ioana"
        steerers={[]}
      />,
    );

    expect(screen.queryByText('process.emptyRuns.body')).not.toBeNull();

    expect(screen.queryByText('process.startFromTasks.open')).not.toBeNull();
  });

  it('offers a new process beside a list that already has runs in it', () => {
    reset();

    render(
      <ProcessScreen
        permissions={['PROCESS_VIEW_OWN', 'PROCESS_INSTANTIATE']}
        viewerId="ioana"
        steerers={[]}
      />,
    );

    expect(screen.queryByText('process.startFromTasks.open')).not.toBeNull();
  });

  it('does not offer a new process to somebody who may not start one', () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByText('process.startFromTasks.open')).toBeNull();
  });

  it('does not invite somebody who may not start a run', () => {
    reset();
    runsState.data = { instances: [] };

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByText('process.emptyRuns.body')).toBeNull();
    expect(screen.queryByText('process.empty.body')).not.toBeNull();
  });

  it('surfaces the step that is waiting for somebody, above the steps', () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByLabelText('process.awaiting.heading')).not.toBeNull();
  });

  it('shows a blocked step with the reason the assignee gave', () => {
    reset();
    const held = instance();
    const [first] = held.steps;
    if (first === undefined) {
      throw new Error('the fixture has no steps');
    }
    first.condition = 'ASSIGNED';
    first.taskState = 'BLOCKED';
    first.blockedReason = 'nu am acces la depozit';
    held.awaitingAssignment = [];
    runState.data = held;

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(
      screen.queryByText('process.step.blocked:{"reason":"nu am acces la depozit"}'),
    ).not.toBeNull();
  });

  it('names what each step is waiting for', () => {
    reset();

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.textContent).toContain('process.step.waitsForNothing');
    expect(container.textContent).toContain('process.step.waitsFor:');
  });

  it('offers the assign control to the person steering this run', () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryByText('process.awaiting.assign')).not.toBeNull();
  });

  it('does not offer the assign control to somebody who neither steers nor holds the grant', () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="andrei" steerers={[]} />);

    expect(screen.queryByLabelText('process.awaiting.heading')).not.toBeNull();
    expect(screen.queryByText('process.awaiting.assign')).toBeNull();
  });

  it('offers the assign control to a holder of the grant who does not steer the run', () => {
    reset();

    render(
      <ProcessScreen
        permissions={['PROCESS_VIEW_OWN', 'PROCESS_ASSIGN_STEP']}
        viewerId="ionut"
        steerers={[]}
      />,
    );

    expect(screen.queryByText('process.awaiting.assign')).not.toBeNull();
  });

  it('names the slow step and how long it has waited, and nobody', () => {
    reset();
    const slow = instance();
    slow.bottleneck = { stepId: 'step-1', waitedMinutes: 2400 };
    slow.steps[0]!.condition = 'ASSIGNED';
    slow.awaitingAssignment = [];
    runState.data = slow;

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.textContent).toContain('process.bottleneck.label');
    expect(container.textContent).toContain('Pregătește echipamentul');
    expect(container.textContent).toContain('process.bottleneck.waiting:{"count":40}');
  });

  it('renders no person on a run, even one whose steps carry names', () => {
    reset();
    const named = instance();
    named.bottleneck = { stepId: 'step-1', waitedMinutes: 2400 };
    named.processOwnerId = 'Ioana Radu';
    runState.data = named;

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.textContent).not.toContain('Ioana Radu');
  });

  it('shows the run it was just told had started', async () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);
    await userEvent.click(screen.getByRole('tab', { name: 'process.section.templates' }));
    await userEvent.click(screen.getByText('stub: start a run'));

    expect(askedForRun).toBe('run-9');
    expect(screen.queryByText('stub: start a run')).toBeNull();
  });

  it('says when the open run could not be loaded', () => {
    reset();
    runState.isError = true;
    runState.data = undefined;

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.queryAllByText('process.loadFailed')).toHaveLength(1);
  });

  it('names the condition of every step', () => {
    reset();

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.textContent).toContain('process.condition.REACHABLE');
    expect(container.textContent).toContain('process.condition.PENDING');
  });

  it('draws the progress of the open run', () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    expect(screen.getByRole('progressbar').getAttribute('aria-valuemax')).toBe('2');
  });

  it('marks the run that is open', async () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);
    await userEvent.click(
      within(screen.getByRole('list', { name: 'process.section.runs' })).getByRole('button'),
    );

    expect(askedForRun).toBe('run-1');
    expect(document.querySelector('[aria-current="true"]')).not.toBeNull();
  });

  it('says on the list row how many steps are waiting for somebody', () => {
    reset();

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.textContent).toContain('process.awaiting.ready:{"count":1}');
  });

  it('says it is loading the run somebody just chose', async () => {
    reset();
    runState.isPending = true;
    runState.data = undefined;

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    await userEvent.click(
      within(screen.getByRole('list', { name: 'process.section.runs' })).getByRole('button'),
    );

    expect(screen.queryByText('process.loading')).not.toBeNull();
  });

  it('does not name a slowest step that has waited less than an hour', () => {
    reset();
    const fresh = instance();
    fresh.bottleneck = { stepId: 'step-1', waitedMinutes: 3 };
    runState.data = fresh;

    const { container } = render(
      <ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />,
    );

    expect(container.textContent).not.toContain('process.bottleneck.label');
  });

  it('offers a way into the operations canvas for the selected run', () => {
    reset();

    render(<ProcessScreen permissions={['PROCESS_VIEW_OWN']} viewerId="ioana" steerers={[]} />);

    const canvas = screen.getByText('process.canvas.open');
    expect(canvas.getAttribute('href')).toBe('/en/canvas/process/run-1');

    expect(canvas.className).toContain('ui-button');
  });

  it('offers assign but not the shape controls to somebody holding only the delegable grant', () => {
    reset();

    render(
      <ProcessScreen
        permissions={['PROCESS_VIEW_OWN', 'PROCESS_ASSIGN_STEP']}
        viewerId="somebody-who-does-not-steer-this"
        steerers={[]}
      />,
    );

    expect(screen.queryByText('process.awaiting.assign')).not.toBeNull();
    expect(screen.queryByText('process.addTask.open')).toBeNull();
  });

  it('offers the shape controls to somebody holding the non-delegable grant', () => {
    reset();

    render(
      <ProcessScreen
        permissions={['PROCESS_VIEW_OWN', 'PROCESS_EDIT_INSTANCE']}
        viewerId="somebody-who-does-not-steer-this"
        steerers={[]}
      />,
    );

    expect(screen.queryByText('process.addTask.open')).not.toBeNull();
  });
});
