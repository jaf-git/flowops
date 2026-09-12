import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import type { JSX, ReactNode } from 'react';

import { DURATION, EASE } from './tokens';

interface RevealProps {
  open: boolean;

  children: ReactNode;

  className?: string;
}

export function Reveal({ open, children, className }: RevealProps): JSX.Element {
  const stillness = useReducedMotion();

  if (stillness === true) {
    return <>{open ? <div className={className}>{children}</div> : null}</>;
  }

  return (
    <AnimatePresence initial={false}>
      {open && (
        <motion.div
          className={className}
          style={{ overflow: 'hidden' }}
          initial={{ height: 0, opacity: 0 }}
          animate={{ height: 'auto', opacity: 1 }}
          exit={{ height: 0, opacity: 0 }}
          transition={{ duration: DURATION.expand, ease: EASE.standard }}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
