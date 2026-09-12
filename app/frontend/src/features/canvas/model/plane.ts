import { readyForSomebody, unsequencedIn, type Band, type BandCard } from './bands';

export const CARD_WIDTH = 208;

export const CARD_HEIGHT = 164;

export const HEAD_WIDTH = 190;

const HEAD_BASE = 214;
const HEAD_READY_BANNER = 46;
const HEAD_LOOSE_BANNER = 42;

export function headHeightOf(band: Band): number {
  const ready = band.running && readyForSomebody(band.cards) !== null ? HEAD_READY_BANNER : 0;
  const loose = unsequencedIn(band.cards).length > 0 ? HEAD_LOOSE_BANNER : 0;
  return HEAD_BASE + ready + loose;
}

export const MOST_ROWS = 3;

const WANTED_RATIO = 1.6;

const COLUMN_GAP = 54;

const ROW_GAP = 28;

const RUN_PADDING = 24;

const RUN_GAP = 22;
const RUN_COLUMN_GAP = 40;

const LANE_GAP = 44;
const SECTION_GAP = 88;

const TITLE_HEIGHT = 46;
const LANE_TITLE_HEIGHT = 30;
const PADDING = 28;

export const COLLAPSED_HEIGHT = 52;

export const COLLAPSED_WIDTH = 232;

export function runsPerColumn(count: number, runWidth: number, runHeight: number): number {
  if (count <= 1 || runWidth <= 0 || runHeight <= 0) {
    return Math.max(1, count);
  }
  const deep = Math.round(Math.sqrt((count * runWidth) / (WANTED_RATIO * runHeight)));
  return Math.min(count, Math.max(1, deep));
}

export interface PlacedCard {
  card: BandCard;

  runId: string;

  sectionKey: string;
  x: number;
  y: number;
}

export interface PlacedRun {
  band: Band;

  sectionKey: string;

  x: number;
  y: number;

  box: { x: number; y: number; width: number; height: number };
}

export type LinkKind = 'DEPENDENCY' | 'MEMBERSHIP';

export interface PlacedLink {
  id: string;
  from: string;
  to: string;

  met: boolean;
  kind: LinkKind;
}

export interface PlacedLane {
  key: string;

  personId?: string;
  x: number;
  y: number;
  width: number;
  height: number;

  titled: boolean;
}

export interface PlacedSection {
  key: string;
  title: string;
  named: boolean;
  collapsed: boolean;

  runCount: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Plane {
  cards: PlacedCard[];
  runs: PlacedRun[];
  lanes: PlacedLane[];
  links: PlacedLink[];
  sections: PlacedSection[];
  width: number;
  height: number;
}

export interface PlaneLaneInput {
  key: string;
  personId?: string;

  titled: boolean;
  bands: readonly Band[];
}

export interface PlaneSectionInput {
  key: string;
  title: string;
  named: boolean;
  lanes: readonly PlaneLaneInput[];
}

export function runNodeId(runId: string): string {
  return `run-${runId}`;
}

function depthsWithin(cards: readonly BandCard[]): Map<string, number> {
  const present = new Set(cards.map((card) => card.id));
  const byId = new Map(cards.map((card) => [card.id, card]));
  const depths = new Map<string, number>();

  const depthOf = (id: string, seen: Set<string>): number => {
    const already = depths.get(id);
    if (already !== undefined) {
      return already;
    }

    if (seen.has(id)) {
      return 0;
    }
    seen.add(id);

    const waits = (byId.get(id)?.dependsOn ?? []).filter((on) => present.has(on));
    const depth = waits.length === 0 ? 0 : Math.max(...waits.map((on) => depthOf(on, seen) + 1));

    depths.set(id, depth);
    return depth;
  };

  cards.forEach((card) => depthOf(card.id, new Set()));
  return depths;
}

function byDepthWithin(cards: readonly BandCard[]): Map<number, BandCard[]> {
  const depths = depthsWithin(cards);
  const grouped = new Map<number, BandCard[]>();

  for (const card of cards) {
    const depth = depths.get(card.id) ?? 0;
    grouped.set(depth, [...(grouped.get(depth) ?? []), card]);
  }
  return grouped;
}

interface MeasuredRun {
  width: number;
  height: number;

  cells: { card: BandCard; dx: number; dy: number }[];
  links: PlacedLink[];

  begins: string[];
}

function measureRun(band: Band): MeasuredRun {
  const stepsX = RUN_PADDING + HEAD_WIDTH + COLUMN_GAP;
  const grouped = byDepthWithin(band.cards);
  const depths = [...grouped.keys()].sort((a, b) => a - b);
  const shallowest = depths[0];

  const cells: MeasuredRun['cells'] = [];
  const links: PlacedLink[] = [];
  const begins: string[] = [];

  let blockX = stepsX;
  let rows = 0;

  for (const depth of depths) {
    const column = grouped.get(depth) ?? [];
    const across = Math.ceil(column.length / MOST_ROWS);

    column.forEach((card, index) => {
      const col = index % across;
      const row = Math.floor(index / across);
      cells.push({
        card,
        dx: blockX + col * (CARD_WIDTH + COLUMN_GAP),
        dy: RUN_PADDING + row * (CARD_HEIGHT + ROW_GAP),
      });
      if (depth === shallowest && col === 0) {
        begins.push(card.id);
      }
    });

    rows = Math.max(rows, Math.ceil(column.length / across));
    blockX += across * (CARD_WIDTH + COLUMN_GAP);
  }

  const present = new Set(band.cards.map((card) => card.id));
  for (const card of band.cards) {
    for (const on of card.dependsOn) {
      if (!present.has(on)) {
        continue;
      }
      const waited = band.cards.find((each) => each.id === on);
      links.push({
        id: `${on}->${card.id}`,
        from: on,
        to: card.id,
        met: waited?.state === 'done',
        kind: 'DEPENDENCY',
      });
    }
  }

  const inner = Math.max(HEAD_WIDTH, blockX - RUN_PADDING - COLUMN_GAP);
  const tall = rows === 0 ? 0 : rows * CARD_HEIGHT + (rows - 1) * ROW_GAP;

  return {
    width: inner + RUN_PADDING * 2,
    height: Math.max(headHeightOf(band), tall) + RUN_PADDING * 2,
    cells,
    links,
    begins,
  };
}

function measureLane(lane: PlaneLaneInput): { width: number; height: number } {
  return layOutLane(lane, 0, 0, '', { cards: [], runs: [], links: [] });
}

function layOutLane(
  lane: PlaneLaneInput,
  originX: number,
  originY: number,
  sectionKey: string,
  into: { cards: PlacedCard[]; runs: PlacedRun[]; links: PlacedLink[] },
): { width: number; height: number } {
  const measured = lane.bands.map((band) => ({ band, box: measureRun(band) }));
  if (measured.length === 0) {
    return { width: 0, height: 0 };
  }

  const deep = runsPerColumn(
    measured.length,
    Math.max(...measured.map((each) => each.box.width)),
    Math.max(...measured.map((each) => each.box.height)),
  );

  const top = originY + (lane.titled ? LANE_TITLE_HEIGHT : 0);
  let columnX = originX;
  let y = top;
  let stacked = 0;
  let columnWidth = 0;
  let lowest = top;

  for (const { band, box } of measured) {
    if (stacked === deep) {
      columnX += columnWidth + RUN_COLUMN_GAP;
      y = top;
      stacked = 0;
      columnWidth = 0;
    }

    into.runs.push({
      band,
      sectionKey,
      x: columnX + RUN_PADDING,
      y: y + RUN_PADDING,
      box: { x: columnX, y, width: box.width, height: box.height },
    });
    for (const cell of box.cells) {
      into.cards.push({
        card: cell.card,
        runId: band.id,
        sectionKey,
        x: columnX + cell.dx,
        y: y + cell.dy,
      });
    }
    into.links.push(...box.links);

    for (const id of box.begins) {
      into.links.push({
        id: `${runNodeId(band.id)}->${id}`,
        from: runNodeId(band.id),
        to: id,
        met: false,
        kind: 'MEMBERSHIP',
      });
    }

    columnWidth = Math.max(columnWidth, box.width);
    y += box.height + RUN_GAP;
    stacked += 1;
    lowest = Math.max(lowest, y);
  }

  return {
    width: columnX - originX + columnWidth,
    height: lowest - RUN_GAP - originY,
  };
}

export function planeOf(
  sections: readonly PlaneSectionInput[],
  collapsed: ReadonlySet<string> = new Set(),
): Plane {
  const cards: PlacedCard[] = [];
  const runs: PlacedRun[] = [];
  const lanes: PlacedLane[] = [];
  const links: PlacedLink[] = [];
  const placed: PlacedSection[] = [];

  let sectionX = 0;
  let tallest = 0;

  for (const section of sections) {
    const filled = section.lanes.filter((lane) => lane.bands.length > 0);
    if (filled.length === 0) {
      continue;
    }

    const runCount = filled.reduce((total, lane) => total + lane.bands.length, 0);

    if (collapsed.has(section.key)) {
      placed.push({
        key: section.key,
        title: section.title,
        named: section.named,
        collapsed: true,
        runCount,
        x: sectionX,
        y: 0,
        width: COLLAPSED_WIDTH,
        height: COLLAPSED_HEIGHT,
      });
      sectionX += COLLAPSED_WIDTH + SECTION_GAP;
      tallest = Math.max(tallest, COLLAPSED_HEIGHT);
      continue;
    }

    const contentX = sectionX + PADDING;

    const measured = filled.map((lane) => ({ lane, box: measureLane(lane) }));
    const across = Math.max(
      1,
      Math.ceil(
        measured.length /
          runsPerColumn(
            measured.length,
            Math.max(...measured.map((each) => each.box.width)),
            Math.max(...measured.map((each) => each.box.height)),
          ),
      ),
    );

    let laneX = contentX;
    let laneY = TITLE_HEIGHT;
    let inRow = 0;
    let rowHeight = 0;
    let widest = 0;
    let deepest = TITLE_HEIGHT;

    for (const { lane } of measured) {
      if (inRow === across) {
        laneY += rowHeight + LANE_GAP;
        laneX = contentX;
        inRow = 0;
        rowHeight = 0;
      }

      const box = layOutLane(lane, laneX, laneY, section.key, { cards, runs, links });
      const tall = box.height + (lane.titled ? LANE_TITLE_HEIGHT : 0);

      lanes.push({
        key: lane.key,
        personId: lane.personId,
        x: laneX,
        y: laneY,
        width: box.width,
        height: tall,
        titled: lane.titled,
      });

      laneX += box.width + LANE_GAP;
      inRow += 1;
      rowHeight = Math.max(rowHeight, tall);
      widest = Math.max(widest, laneX - LANE_GAP - contentX);
      deepest = Math.max(deepest, laneY + tall);
    }

    const width = widest + PADDING * 2;
    const height = deepest + PADDING;
    placed.push({
      key: section.key,
      title: section.title,
      named: section.named,
      collapsed: false,
      runCount,
      x: sectionX,
      y: 0,
      width,
      height,
    });

    sectionX += width + SECTION_GAP;
    tallest = Math.max(tallest, height);
  }

  return {
    cards,
    runs,
    lanes,
    links,
    sections: placed,
    width: Math.max(0, sectionX - SECTION_GAP),
    height: tallest,
  };
}
