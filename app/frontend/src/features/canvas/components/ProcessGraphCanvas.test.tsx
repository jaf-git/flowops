// @vitest-environment jsdom

import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { instanceFixture, INSTANCE_NOW } from '../model/instance';
import { drawnEdges, edgeLabel, edgeStroke } from '../model/layout';
import { DOT_MET, DOT_UNMET, ProcessGraphCanvas } from './ProcessGraphCanvas';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      if (typeof phrase !== 'string') {
        return key;
      }

      return phrase.replace(/{{(\w+)}}/g, (_, name: string) => values?.[name] ?? '');
    },
    i18n: { language: 'en' },
  }),
}));

beforeAll(() => {
  global.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as unknown as typeof ResizeObserver;

  global.DOMMatrixReadOnly = class {
    m22 = 1;
  } as unknown as typeof DOMMatrixReadOnly;

  Object.defineProperties(global.HTMLElement.prototype, {
    offsetHeight: { get: () => 800 },
    offsetWidth: { get: () => 1200 },
  });

  (
    global as unknown as { SVGElement: { prototype: Record<string, unknown> } }
  ).SVGElement.prototype.getBBox = () => ({ x: 0, y: 0, width: 0, height: 0 });
});

const { steps, edges } = instanceFixture((key) => {
  const phrase = key
    .split('.')
    .reduce<unknown>(
      (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
      en as unknown,
    );

  return typeof phrase === 'string' ? phrase : key;
});

function plane() {
  return render(<ProcessGraphCanvas steps={steps} edges={edges} now={INSTANCE_NOW} />);
}

afterEach(cleanup);

describe('the plane', () => {
  it('draws every step of the instance', () => {
    plane();

    for (const step of steps) {
      expect(screen.getByText(step.title), `${step.id} is missing from the plane`).toBeTruthy();
    }
  });

  it('hands React Flow the size it would otherwise stop to measure', () => {
    const { container } = plane();

    const nodes = [...container.querySelectorAll('.react-flow__node')] as HTMLElement[];

    expect(nodes.length).toBe(steps.length);

    for (const node of nodes) {
      expect(node.style.width).not.toBe('');
    }
  });
});

describe('what the plane refuses to be', () => {
  it('offers nothing anybody could drag a dependency out of', () => {
    const { container } = plane();

    expect(container.querySelectorAll('.react-flow__handle.connectable')).toHaveLength(0);
    expect(container.querySelectorAll('.react-flow__handle.connectionindicator')).toHaveLength(0);
  });

  it('leaves every anchor invisible and untouchable', () => {
    const { container } = plane();
    const anchors = [...container.querySelectorAll('.react-flow__handle')];

    expect(anchors.length).toBeGreaterThan(0);

    for (const anchor of anchors) {
      const style = (anchor as HTMLElement).style;

      expect(style.opacity).toBe('0');
      expect(style.pointerEvents).toBe('none');
    }
  });

  it('selects a step from the keyboard, not by mouse alone', async () => {
    const chosen = vi.fn();
    render(<ProcessGraphCanvas steps={steps} edges={edges} now={INSTANCE_NOW} onSelect={chosen} />);

    const node = screen.getByRole('button', { name: new RegExp(steps[0]?.title ?? '') });
    node.focus();
    await userEvent.keyboard('{Enter}');

    expect(chosen).toHaveBeenCalledWith(steps[0]?.id);
  });

  it('lets a node be moved but never lets a dependency be drawn', () => {
    const { container } = plane();

    expect(container.querySelectorAll('.react-flow__node.draggable').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('.react-flow__node.connectable')).toHaveLength(0);
  });
});

describe('the look, between the glance and the read', () => {
  function firstNode(): HTMLElement {
    return screen.getByRole('button', { name: new RegExp(steps[0]?.title ?? '') });
  }

  it('shows no card until somebody looks at something', () => {
    plane();

    expect(screen.queryByTestId('node-overview-card')).toBeNull();
  });

  it('reveals the card while a node is hovered, and takes it back afterwards', async () => {
    plane();

    await userEvent.hover(firstNode());
    expect(screen.getByTestId('node-overview-card')).toBeTruthy();

    await userEvent.unhover(firstNode());
    expect(screen.queryByTestId('node-overview-card')).toBeNull();
  });

  it('gives the keyboard the same card the mouse gets', () => {
    plane();
    const node = firstNode();

    act(() => node.focus());

    const card = screen.getByTestId('node-overview-card');

    expect(card).toBeTruthy();

    expect(node.getAttribute('aria-describedby')).toBe(card.id);

    act(() => node.blur());
    expect(screen.queryByTestId('node-overview-card')).toBeNull();
  });

  it('leaves the card unable to take the pointer from the plane beneath it', async () => {
    plane();

    await userEvent.hover(firstNode());

    expect(screen.getByTestId('node-overview-card').style.pointerEvents).toBe('none');
  });
});

describe('what an edge says', () => {
  it('marks a met dependency and an unmet one differently in stroke, not only in colour', () => {
    const met = edgeStroke({ from: '1', to: '3', satisfied: true });
    const unmet = edgeStroke({ from: '3', to: '6', satisfied: false });

    expect(met.strokeDasharray).toBeUndefined();
    expect(unmet.strokeDasharray).toBeTruthy();

    expect(met.stroke).not.toBe(unmet.stroke);
  });

  it('says why a dependency is not met, rather than only that it is not', () => {
    const translate = (key: string): string => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      return typeof phrase === 'string' ? phrase : key;
    };

    const labels = edges.map((edge) => edgeLabel(edge, steps, translate));

    expect(labels).toContain(en.canvas.edge.satisfied);
    expect(labels).toContain(en.canvas.edge.blocked);
    expect(labels).toContain(en.canvas.edge.inProgress);
  });

  it('defines both endpoint dots, and names them the way React Flow expects', () => {
    const { container } = plane();

    expect(container.querySelector(`#${DOT_MET}`)).toBeTruthy();
    expect(container.querySelector(`#${DOT_UNMET}`)).toBeTruthy();

    for (const edge of drawnEdges(steps, edges, (key) => key, { met: DOT_MET, unmet: DOT_UNMET })) {
      expect(String(edge.markerStart)).not.toContain('url(');
      expect(String(edge.markerEnd)).not.toContain('url(');
      expect([DOT_MET, DOT_UNMET]).toContain(edge.markerEnd);
    }
  });

  it('never leaves an edge saying nothing at all', () => {
    const translate = (key: string): string => key;

    for (const edge of edges) {
      expect(edgeLabel(edge, steps, translate), `${edge.from}->${edge.to} is unlabelled`).not.toBe(
        '',
      );
    }
  });
});
