import { MotionConfig } from 'framer-motion';
import type { JSX, ReactNode } from 'react';

import { DURATION, EASE } from './tokens';

interface MotionProviderProps {
  children: ReactNode;
}

export function MotionProvider({ children }: MotionProviderProps): JSX.Element {
  return (
    <MotionConfig
      reducedMotion="user"
      transition={{ duration: DURATION.expand, ease: EASE.standard }}
    >
      {children}
    </MotionConfig>
  );
}
