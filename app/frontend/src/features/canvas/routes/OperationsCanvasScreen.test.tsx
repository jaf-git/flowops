// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { ProcessGraphCanvas } from '../components/ProcessGraphCanvas';
import { instanceFixture } from '../model/instance';
import { OperationsCanvasScreen } from './OperationsCanvasScreen';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const phrase = key
        .split('.')
        .reduce<unknown>(
          (branch, segment) => (branch as Record<string, unknown> | undefined)?.[segment],
          en as unknown,
        );

      return typeof phrase === 'string' ? phrase : key;
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

afterEach(cleanup);

describe('the operations canvas screen', () => {
  it('says plainly that the process on it is invented', () => {
    render(<OperationsCanvasScreen />);

    expect(screen.getByText(en.canvas.instance.lead)).toBeTruthy();
  });

  it('names the instance it is drawing', () => {
    render(<OperationsCanvasScreen />);

    expect(screen.getByText(en.canvas.instance.title)).toBeTruthy();
  });

  it('takes every word of the instance from the translation resources', () => {
    const { steps } = instanceFixture((key) => key);

    const untranslated = steps.flatMap((step) =>
      [
        step.title,
        step.blockedReason,
        ...(step.meta ?? []).flatMap((row) => [row.label, row.value]),
      ].filter((text): text is string => text !== undefined && !text.startsWith('canvas.')),
    );

    expect(untranslated).toEqual([]);
  });
});

describe('a process with nothing in it', () => {
  it('says so, rather than drawing an empty grid', () => {
    render(<ProcessGraphCanvas steps={[]} edges={[]} />);

    expect(screen.getByTestId('canvas-plane-empty')).toBeTruthy();
    expect(screen.getByText(en.canvas.instance.empty)).toBeTruthy();
  });
});
