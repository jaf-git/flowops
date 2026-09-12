import { createContext } from 'react';

import type { Announcement, Notice } from './Notice';

export interface NoticeDesk {
  readonly standing: readonly Notice[];

  readonly announce: (announcement: Announcement) => void;

  readonly dismiss: (id: string) => void;
}

export const NoticeContext = createContext<NoticeDesk | undefined>(undefined);
