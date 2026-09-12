import type { ReactNode } from 'react';

export type NoticeTone = 'done' | 'failed' | 'offer';

export interface Notice {
  readonly id: string;

  readonly tone: NoticeTone;

  readonly message: string;

  readonly detail?: string;

  readonly action?: ReactNode;

  readonly dwellMs?: number;
}

export interface Announcement {
  readonly tone: NoticeTone;

  readonly message: string;

  readonly detail?: string;

  readonly action?: ReactNode;

  readonly dwellMs?: number;
}

export const NOTICE_DWELL_MS = 6_000;

export const NOTICES_SHOWN = 3;

export function afterAnnouncing(
  standing: readonly Notice[],
  arriving: Notice,
  shown = NOTICES_SHOWN,
): readonly Notice[] {
  return [...standing, arriving].slice(-shown);
}

export function dwellFor(notice: Pick<Notice, 'tone' | 'dwellMs'>): number | undefined {
  if (notice.tone === 'failed') {
    return undefined;
  }
  return notice.dwellMs ?? NOTICE_DWELL_MS;
}
