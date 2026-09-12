import { useMemo, useState, type JSX, type ReactNode } from 'react';

import { ReadingMessageContext, type Reading } from '../hooks/useReadingMessage';

export function ReadingMessageProvider({ children }: { children: ReactNode }): JSX.Element {
  const [messageId, setMessageId] = useState<string | null>(null);

  const reading = useMemo<Reading>(() => ({ messageId, read: setMessageId }), [messageId]);

  return (
    <ReadingMessageContext.Provider value={reading}>{children}</ReadingMessageContext.Provider>
  );
}
