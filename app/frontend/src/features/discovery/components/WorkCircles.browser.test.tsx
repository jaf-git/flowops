import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { JSX } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '../../../index.css';
import '../../../shell.css';

import en from '../../../i18n/locales/en/common.json';
import { WorkCircles } from './WorkCircles';

let lengthened = false;

function stretch(value: string): string {
  return value
    .split(' ')
    .map((word) => (word.length > 3 ? word + word.slice(0, Math.ceil(word.length * 0.3)) : word))
    .join(' ');
}

function lookup(key: string, vars?: Record<string, unknown>): string {
  const at = (path: string): unknown =>
    path
      .split('.')
      .reduce<unknown>(
        (node, step) =>
          typeof node === 'object' && node !== null
            ? (node as Record<string, unknown>)[step]
            : undefined,
        en,
      );

  const count = vars?.count;
  const plural =
    typeof count === 'number' ? at(`${key}_${count === 1 ? 'one' : 'other'}`) : undefined;
  const found = plural ?? at(key);
  const text = typeof found === 'string' ? found : key;
  const filled = Object.entries(vars ?? {}).reduce(
    (sentence, [name, value]) => sentence.replaceAll(`{{${name}}}`, String(value)),
    text,
  );

  return lengthened ? stretch(filled) : filled;
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) => lookup(key, vars),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
  I18nextProvider: ({ children }: { children: unknown }) => children,
  Trans: ({ children }: { children: unknown }) => children,
}));

const apiRequest = vi.fn<(path: string, init?: RequestInit) => Promise<unknown>>();

vi.mock('../../../shared/api/client', () => ({
  apiRequest: (path: string, init?: RequestInit) => apiRequest(path, init),
  ApiError: class extends Error {},
}));

const JOBS = [{ jobId: 'j2', name: 'Bistro Verde — autumn menu photography', guessed: false }];

const DESTINATION = 'Bistro Verde — autumn menu photography › PHOTOGRAPHY';

const VOCABULARY = [
  'PHOTOGRAPHY',
  'COPYWRITING',
  'CLIENT_APPROVAL',
  'MOTION_DESIGN',
  'MEDIA_BUYING',
  'REPORTING',
];

function serve(): void {
  apiRequest.mockImplementation((path) => {
    if (path.startsWith('/discovery/jobs')) {
      return Promise.resolve(JOBS);
    }

    if (path.includes('/marks')) {
      return Promise.resolve([]);
    }

    if (path.startsWith('/discovery/graph/job/')) {
      return Promise.resolve({
        nodes: VOCABULARY.map((workType, index) => ({
          nodeId: `n${index}`,
          bracketId: `b${index}`,
          workType,
          boundary: false,
        })),
        edges: [],
      });
    }

    if (path.startsWith('/discovery/brackets/preview')) {
      return Promise.resolve({
        describe: DESTINATION,
        joins: true,
        bracketId: 'b1',
        workType: 'PHOTOGRAPHY',

        verbs: ['ADD', 'CREATE', 'JOIN'],
        othersHere: [
          {
            bracketId: 'b9',
            jobId: 'j2',
            workType: 'PHOTOGRAPHY',
            destination: DESTINATION,
            performerId: 'p9',
            performerName: 'Andrei Munteanu',
          },
        ],
      });
    }

    if (path.startsWith('/discovery/graph/work-type')) {
      return Promise.resolve({
        workType: 'PHOTOGRAPHY',
        neverUsedBefore: false,
        closestExisting: [],
      });
    }

    return Promise.resolve([]);
  });
}

const PEOPLE = [
  { id: 'p1', displayName: 'Ioana Rădulescu-Popa' },
  { id: 'p9', displayName: 'Andrei Munteanu' },
];

let cache: QueryClient;

function InAThreadColumn(): JSX.Element {
  return (
    <QueryClientProvider client={cache}>
      <div className="probe-column" style={{ width: 640 }}>
        <article className="fo-message fo-message-card">
          <div
            aria-hidden="true"
            style={{
              width: 28,
              height: 28,
              flexShrink: 0,
              borderRadius: 'var(--radius-pill)',
              background: 'var(--surface-sunken)',
            }}
          />
          <div style={{ flex: 1, minWidth: 0 }}>
            <header>
              <span>Ioana Rădulescu-Popa</span>
            </header>
            <p style={{ margin: 0 }}>
              Andrei, poți să faci pozele pentru meniul de toamnă până joi dimineață?
            </p>
          </div>

          <WorkCircles
            conversationId="c1"
            messageId="m1"
            viewerId="p1"
            permissions={['WORK_NODE_MARK']}
            people={PEOPLE}
            everybody={PEOPLE}
            onBringIn={() => Promise.resolve()}
          />
        </article>
      </div>
    </QueryClientProvider>
  );
}

function overlaps(a: DOMRect, b: DOMRect): boolean {
  return (
    a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1
  );
}

function boxesOf(selector: string): DOMRect[] {
  return Array.from(document.querySelectorAll(selector)).map((node) =>
    node.getBoundingClientRect(),
  );
}

async function openEverything(): Promise<void> {
  render(<InAThreadColumn />);

  fireEvent.click(
    await screen.findByRole('button', { name: lookup('discovery.circles.openLabel') }),
  );

  await screen.findByText(lookup('discovery.mark.willJoin', { where: DESTINATION }));

  fireEvent.click(document.querySelector('.fo-mark-kind') as HTMLElement);
  await screen.findByRole('group');
}

beforeEach(() => {
  apiRequest.mockReset();
  serve();
  cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  lengthened = false;
});

afterEach(cleanup);

describe.each([
  ['English', false],
  ['English at +30%', true],
])('the message card and the flow joined beneath it, %s', (_name, longer) => {
  beforeEach(() => {
    lengthened = longer;
  });

  it('keeps every control inside the thread column it was given', async () => {
    await openEverything();

    const column = document.querySelector('.probe-column') as HTMLElement;
    const edge = column.getBoundingClientRect().right;

    for (const node of document.querySelectorAll('.fo-message *')) {
      const element = node as HTMLElement;
      const box = element.getBoundingClientRect();
      if (box.width === 0) {
        continue;
      }

      expect(
        Math.round(box.right),
        `${element.className || element.nodeName} crosses the column's right edge`,
      ).toBeLessThanOrEqual(Math.round(edge) + 1);
    }
  });

  it('never forces the page body to scroll sideways', async () => {
    await openEverything();

    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
      document.documentElement.clientWidth,
    );
  });

  it('paints no control on top of another', async () => {
    await openEverything();

    const buttons = boxesOf('.fo-message button, .fo-message select');
    expect(buttons.length).toBeGreaterThanOrEqual(6);

    for (const [i, a] of buttons.entries()) {
      for (const [j, b] of buttons.entries()) {
        if (j <= i) {
          continue;
        }

        expect(
          overlaps(a, b),
          `controls ${i} and ${j} share pixels at ${lengthened ? '+30%' : 'English'}`,
        ).toBe(false);
      }
    }
  });

  it('shows every label in full rather than clipping it', async () => {
    await openEverything();

    for (const node of document.querySelectorAll('.fo-message button')) {
      const element = node as HTMLElement;
      expect(
        element.scrollWidth,
        `${element.textContent ?? ''} is clipped sideways`,
      ).toBeLessThanOrEqual(element.clientWidth + 1);
      expect(
        element.scrollHeight,
        `${element.textContent ?? ''} is clipped vertically`,
      ).toBeLessThanOrEqual(element.clientHeight + 1);
    }
  });

  it('joins the flow panel to the card rather than floating it inside', async () => {
    await openEverything();

    const card = document.querySelector('.fo-message-card') as HTMLElement;
    const flow = document.querySelector('.fo-message-flow') as HTMLElement;

    const outer = card.getBoundingClientRect();
    const inner = flow.getBoundingClientRect();

    expect(Math.round(inner.left)).toBe(Math.round(outer.left));
    expect(Math.round(inner.right)).toBe(Math.round(outer.right));

    expect(Math.abs(inner.bottom - outer.bottom)).toBeLessThanOrEqual(1);
  });
});
