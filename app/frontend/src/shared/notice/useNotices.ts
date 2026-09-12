import { useContext } from 'react';

import type { Announcement } from './Notice';
import { NoticeContext, type NoticeDesk } from './NoticeContext';

export function useNotices(): NoticeDesk {
  const desk = useContext(NoticeContext);
  if (desk === undefined) {
    throw new Error('A notice needs a NoticeProvider above it.');
  }
  return desk;
}

export function useAnnounce(): (announcement: Announcement) => void {
  return useNotices().announce;
}
