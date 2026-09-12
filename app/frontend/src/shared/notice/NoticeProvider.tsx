import { useCallback, useMemo, useState, type JSX, type ReactNode } from 'react';

import { afterAnnouncing, type Announcement, type Notice } from './Notice';
import { NoticeContext } from './NoticeContext';

interface NoticeProviderProps {
  children: ReactNode;
}

export function NoticeProvider({ children }: NoticeProviderProps): JSX.Element {
  const [standing, setStanding] = useState<readonly Notice[]>([]);

  const announce = useCallback((announcement: Announcement) => {
    setStanding((held) => afterAnnouncing(held, { ...announcement, id: crypto.randomUUID() }));
  }, []);

  const dismiss = useCallback((id: string) => {
    setStanding((held) => held.filter((one) => one.id !== id));
  }, []);

  const desk = useMemo(() => ({ standing, announce, dismiss }), [standing, announce, dismiss]);

  return <NoticeContext.Provider value={desk}>{children}</NoticeContext.Provider>;
}
