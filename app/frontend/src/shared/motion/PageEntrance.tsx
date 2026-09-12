import { motion, useReducedMotion } from 'framer-motion';
import type { JSX, ReactNode } from 'react';

import { DURATION, EASE, RISE_PX } from './tokens';

interface PageEntranceProps {
  routeKey: string;

  children: ReactNode;

  className?: string;
}

export function PageEntrance({ routeKey, children, className }: PageEntranceProps): JSX.Element {
  const stillness = useReducedMotion();

  if (stillness === true) {
    return <main className={className}>{children}</main>;
  }

  return (
    <motion.main
      key={routeKey}
      className={className}
      initial={{ opacity: 0, y: RISE_PX }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: DURATION.transition, ease: EASE.spatial }}
    >
      {children}
    </motion.main>
  );
}
