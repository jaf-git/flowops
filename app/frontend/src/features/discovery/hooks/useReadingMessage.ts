import { createContext, useContext } from 'react';

export interface Reading {
  readonly messageId: string | null;
  readonly read: (messageId: string | null) => void;
}

export const ReadingMessageContext = createContext<Reading | null>(null);

export function useReadingMessage(): Reading | null {
  return useContext(ReadingMessageContext);
}
