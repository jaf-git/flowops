import type { BandCard } from './bands';

export interface CardTip {
  card: BandCard;
  x: number;
  y: number;

  below: boolean;
}

export interface AnchorBox {
  top: number;
  bottom: number;
  left: number;
  width: number;
}

const ROOM_NEEDED = 200;

const CLEARANCE = 8;

export function anchorTip(card: BandCard, box: AnchorBox): CardTip {
  const below = box.top < ROOM_NEEDED;

  return {
    card,
    x: box.left + box.width / 2,
    y: below ? box.bottom + CLEARANCE : box.top - CLEARANCE,
    below,
  };
}
