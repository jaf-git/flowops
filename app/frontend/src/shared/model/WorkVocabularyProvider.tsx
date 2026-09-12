import type { JSX, ReactNode } from 'react';

import { WorkVocabularyContext, type WorkVocabulary } from './workVocabulary';

export function WorkVocabularyProvider({
  vocabulary,
  children,
}: {
  vocabulary: WorkVocabulary;
  children: ReactNode;
}): JSX.Element {
  return (
    <WorkVocabularyContext.Provider value={vocabulary}>{children}</WorkVocabularyContext.Provider>
  );
}
