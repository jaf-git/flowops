// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import type { Band } from '../model/bands';
import { OperationsBoard } from './OperationsBoard';

beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as unknown as typeof ResizeObserver;
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options === undefined ? key : `${key} ${Object.values(options).join(' ')}`,
    i18n: { language: 'en' },
  }),
}));

afterEach(cleanup);

function band(over: Partial<Band> = {}): Band {
  return {
    id: 'i1',
    name: 'Lansare produs',
    running: true,
    paused: false,
    ownerId: 'p1',
    progress: { closed: 1, total: 4 },
    cards: [
      {
        id: 's1',
        title: 'Pregătește specificația',
        taskId: 't-done',
        state: 'done',
        blockedReason: null,
        taskState: 'CLOSED',
        bottleneckMinutes: null,

        description: null,
        assigneeId: null,
        assigneeName: null,
        deadline: null,
        atRisk: false,
        dependsOn: [],
      },
      {
        id: 's2',
        title: 'Sună furnizorul',
        taskId: 't-blocked',
        state: 'blocked',
        blockedReason: 'aștept avizul',
        taskState: 'BLOCKED',
        bottleneckMinutes: 300,

        description: null,
        assigneeId: null,
        assigneeName: null,
        deadline: null,
        atRisk: false,
        dependsOn: [],
      },
    ],
    ...over,
  };
}

function renderBoard(
  bands: Band[],
  onAddTask: (id: string) => void = () => {},
  onOpenTask: (id: string) => void = () => {},
): void {
  render(
    <MemoryRouter>
      <OperationsBoard
        sections={[{ key: 'all', title: 'All', named: false, bands: bands }]}
        loading={false}
        locale="en"
        ownerNames={new Map([['p1', 'Maria Ionescu']])}
        onAddTask={onAddTask}
        onOpenTask={onOpenTask}
      />
    </MemoryRouter>,
  );
}

describe('the operations board', () => {
  it('draws a band per process with its name, progress and owner', () => {
    renderBoard([band(), band({ id: 'i2', name: 'Inventar Q3', ownerId: 'gone' })]);

    expect(screen.getByText('Lansare produs')).toBeDefined();
    expect(screen.getByText('Inventar Q3')).toBeDefined();
    expect(screen.getAllByText(/^canvas\.board\.progress/)).toHaveLength(2);
    expect(screen.getByText('Maria Ionescu')).toBeDefined();
  });

  it('names an owner the roster cannot as a former member, never as a blank', () => {
    renderBoard([band({ ownerId: 'erased' })]);

    expect(screen.getByText('canvas.board.formerMember')).toBeDefined();
  });

  it('shows the blocked step in the assignee"s own words, and the bottleneck wait on it', () => {
    renderBoard([band()]);

    expect(screen.getByText('aștept avizul')).toBeDefined();
    expect(screen.getByText('canvas.board.waited 5h')).toBeDefined();
  });

  it('never renders a wait that happened as zero, and agrees with Reports about how long it was', () => {
    const blocked = band().cards[1];
    if (blocked === undefined) {
      throw new Error('the default band must carry a second card');
    }
    renderBoard([band({ cards: [{ ...blocked, id: 's-short', bottleneckMinutes: 29 }] })]);

    expect(screen.getByText('canvas.board.waited 29m')).toBeDefined();
    expect(screen.queryByText(/waited 0/)).toBeNull();
  });

  it('dims a filtered state rather than removing it, so the flow keeps its shape', () => {
    const { container } = (() => {
      renderBoard([band()]);
      return { container: document.body };
    })();

    const legendDone = screen
      .getAllByRole('button', { pressed: true })
      .find((button) => button.textContent?.includes('state.done'));
    fireEvent.click(legendDone as HTMLElement);

    const doneCard = container.querySelector('[data-state="done"]') as HTMLElement;
    const blockedCard = container.querySelector('[data-state="blocked"]') as HTMLElement;

    expect(doneCard.style.opacity).toBe('0.62');
    expect(blockedCard.style.opacity).toBe('1');

    expect(screen.getByText('Pregătește specificația')).toBeDefined();
  });

  it('offers the ghost card on a running band and asks the caller to add there', () => {
    const asked: string[] = [];
    renderBoard([band()], (id) => asked.push(id));

    fireEvent.click(screen.getByText('canvas.board.addTask'));

    expect(asked).toEqual(['i1']);
  });

  it('disables the ghost card on a complete run, because the endpoint would refuse it', () => {
    renderBoard([band({ running: false })]);

    expect((screen.getByText('canvas.board.addTask') as HTMLButtonElement).disabled).toBe(true);
  });

  it('opens the task behind a card, and offers nothing on a planned step', () => {
    const opened: string[] = [];
    renderBoard(
      [
        band({
          cards: [
            {
              id: 's1',
              title: 'Sună furnizorul',
              taskId: 't1',
              state: 'inProgress',
              blockedReason: null,
              taskState: 'IN_PROGRESS',
              bottleneckMinutes: null,

              description: null,
              assigneeId: null,
              assigneeName: null,
              deadline: null,
              atRisk: false,
              dependsOn: [],
            },
            {
              id: 's2',
              title: 'Planificat doar',
              taskId: null,
              state: 'notStarted',
              blockedReason: null,
              taskState: null,
              bottleneckMinutes: null,

              description: null,
              assigneeId: null,
              assigneeName: null,
              deadline: null,
              atRisk: false,
              dependsOn: [],
            },
          ],
        }),
      ],
      () => {},
      (id) => opened.push(id),
    );

    fireEvent.click(screen.getByText('Sună furnizorul'));
    expect(opened).toEqual(['t1']);

    const planned = screen.getByText('Planificat doar').closest('article') as HTMLElement;
    expect(planned.getAttribute('role')).toBeNull();
    fireEvent.click(planned);
    expect(opened).toEqual(['t1']);
  });

  it('offers exactly four legend states, each of which a card can actually reach', () => {
    renderBoard([band()]);

    const legend = screen.getAllByRole('button', { pressed: true });
    expect(legend).toHaveLength(4);
    expect(screen.queryByText('canvas.board.state.atRisk')).toBeNull();
  });

  it('pans when the plane is dragged', () => {
    renderBoard([band()]);
    const plane = capturablePlane();
    plane.scrollLeft = 100;
    plane.scrollTop = 40;

    fireEvent.pointerDown(plane, { button: 0, pointerId: 1, clientX: 300, clientY: 200 });
    fireEvent.pointerMove(plane, { pointerId: 1, clientX: 260, clientY: 185 });

    expect(plane.scrollLeft).toBe(140);
    expect(plane.scrollTop).toBe(55);

    fireEvent.pointerUp(plane, { pointerId: 1 });
    fireEvent.pointerMove(plane, { pointerId: 1, clientX: 100, clientY: 100 });
    expect(plane.scrollLeft).toBe(140);
  });

  it('opens a card on a press that does not travel', () => {
    const opened = vi.fn();
    renderBoard([band()], () => {}, opened);
    const plane = capturablePlane();
    const card = cardTitled('Pregătește specificația');

    fireEvent.pointerDown(card, { button: 0, pointerId: 1, clientX: 300, clientY: 200 });

    fireEvent.pointerMove(plane, { pointerId: 1, clientX: 302, clientY: 201 });
    fireEvent.pointerUp(plane, { pointerId: 1 });
    fireEvent.click(card);

    expect(opened).toHaveBeenCalled();
  });

  it('pans when the drag starts on a card, and does not open it', () => {
    const opened = vi.fn();
    renderBoard([band()], () => {}, opened);
    const plane = capturablePlane();
    plane.scrollLeft = 0;
    const card = cardTitled('Pregătește specificația');

    fireEvent.pointerDown(card, { button: 0, pointerId: 1, clientX: 300, clientY: 200 });
    fireEvent.pointerMove(plane, { pointerId: 1, clientX: 240, clientY: 200 });
    fireEvent.pointerUp(plane, { pointerId: 1 });

    fireEvent.click(card);

    expect(plane.scrollLeft).toBe(60);
    expect(opened).not.toHaveBeenCalled();
  });

  it('zooms on the wheel', () => {
    renderBoard([band()]);
    const plane = capturablePlane();
    expect(screen.getByText('100%')).toBeDefined();

    fireEvent.wheel(plane, { deltaY: -100 });

    expect(screen.getByText('110%')).toBeDefined();
  });

  it('zooms after the board finishes loading, not only when it renders loaded', () => {
    const { rerender } = render(
      <MemoryRouter>
        <OperationsBoard
          sections={[{ key: 'all', title: 'All', named: false, bands: [] }]}
          loading
          locale="en"
          ownerNames={new Map()}
          onAddTask={() => {}}
          onOpenTask={() => {}}
        />
      </MemoryRouter>,
    );
    expect(document.querySelector('.canvas-plane')).toBeNull();

    rerender(
      <MemoryRouter>
        <OperationsBoard
          sections={[{ key: 'all', title: 'All', named: false, bands: [band()] }]}
          loading={false}
          locale="en"
          ownerNames={new Map([['p1', 'Maria Ionescu']])}
          onAddTask={() => {}}
          onOpenTask={() => {}}
        />
      </MemoryRouter>,
    );

    fireEvent.wheel(document.querySelector('.canvas-plane') as HTMLDivElement, { deltaY: -100 });

    expect(screen.getByText('110%')).toBeDefined();
  });

  it('shows the full title and the blocked reason on hover, and clears on leave', () => {
    renderBoard([band()]);
    const blocked = screen.getByText('Sună furnizorul').closest('[role="button"]') as HTMLElement;

    fireEvent.mouseEnter(blocked);

    const tip = document.querySelector('[role="tooltip"]') as HTMLElement;
    expect(tip).not.toBeNull();
    expect(tip.textContent).toContain('Sună furnizorul');
    expect(tip.textContent).toContain('aștept avizul');

    fireEvent.mouseLeave(blocked);
    expect(document.querySelector('[role="tooltip"]')).toBeNull();
  });

  it('carries the description, assignee and deadline, and omits what it does not know', () => {
    renderBoard([
      band({
        cards: [
          {
            id: 's9',
            title: 'Sună furnizorul',
            taskId: 't9',
            state: 'inProgress',
            blockedReason: null,
            taskState: 'IN_PROGRESS',
            bottleneckMinutes: null,
            description: 'Confirmă termenul de livrare pentru comanda de mobilier.',
            assigneeId: 'p-andrei',
            assigneeName: 'Andrei Munteanu',
            deadline: '2026-08-21T09:00:00Z',
            atRisk: true,
            dependsOn: [],
          },
        ],
      }),
    ]);

    fireEvent.mouseEnter(
      screen.getByText('Sună furnizorul').closest('[role="button"]') as HTMLElement,
    );

    const tip = document.querySelector('[role="tooltip"]') as HTMLElement;
    expect(tip.textContent).toContain('Confirmă termenul de livrare');
    expect(tip.textContent).toContain('Andrei Munteanu');
    expect(tip.textContent).toContain('canvas.tip.deadline');
    expect(tip.textContent).toContain('task.atRisk');
  });

  it('renders no field rows at all on a card that knows neither', () => {
    renderBoard([
      band({
        cards: [
          {
            id: 's10',
            title: 'Pas neplanificat',
            taskId: 't10',
            state: 'notStarted',
            blockedReason: null,
            taskState: null,
            bottleneckMinutes: null,
            description: null,
            assigneeId: null,
            assigneeName: null,
            deadline: null,
            atRisk: false,
            dependsOn: [],
          },
        ],
      }),
    ]);

    fireEvent.mouseEnter(
      screen.getByText('Pas neplanificat').closest('[role="button"]') as HTMLElement,
    );

    const tip = document.querySelector('[role="tooltip"]') as HTMLElement;
    expect(tip.querySelector('dl')).toBeNull();
    expect(tip.textContent).not.toContain('canvas.tip.assignee');
  });

  it('shows the same card on focus, so a keyboard reaches it', () => {
    renderBoard([band()]);
    const card = screen.getByText('Sună furnizorul').closest('[role="button"]') as HTMLElement;

    fireEvent.focus(card);
    expect(document.querySelector('[role="tooltip"]')?.textContent).toContain('aștept avizul');

    fireEvent.blur(card);
    expect(document.querySelector('[role="tooltip"]')).toBeNull();
  });

  function capturablePlane(): HTMLDivElement {
    const plane = document.querySelector('.canvas-plane') as HTMLDivElement;
    plane.setPointerCapture = () => {};
    plane.releasePointerCapture = () => {};
    return plane;
  }

  function cardTitled(title: string): HTMLElement {
    return screen.getByText(title).closest('[role="button"]') as HTMLElement;
  }
});
