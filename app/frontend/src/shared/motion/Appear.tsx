import { motion, useReducedMotion } from 'framer-motion';
import type { JSX, ReactNode } from 'react';

import { DURATION, EASE, RISE_PX, staggerFor } from './tokens';

type AppearFrom = 'below' | 'right' | 'still';

interface AppearProps {
  children: ReactNode;

  index?: number;

  from?: AppearFrom;

  duration?: number;

  className?: string;
}

function offset(from: AppearFrom): { x?: number; y?: number } {
  if (from === 'below') {
    return { y: RISE_PX };
  }
  if (from === 'right') {
    return { x: RISE_PX };
  }
  return {};
}

export function Appear({
  children,
  index = 0,
  from = 'below',
  duration = DURATION.transition,
  className,
}: AppearProps): JSX.Element {
  const stillness = useReducedMotion();

  if (stillness === true) {
    return <div className={className}>{children}</div>;
  }

  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, ...offset(from) }}
      animate={{ opacity: 1, x: 0, y: 0 }}
      transition={{ duration, ease: EASE.spatial, delay: staggerFor(index) }}
    >
      {children}
    </motion.div>
  );
}
